package thunder.hack.features.modules.combat;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ChargedProjectilesComponent;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.item.ArrowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.Action;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import thunder.hack.core.Managers;
import thunder.hack.core.manager.client.AsyncManager;
import thunder.hack.features.modules.Module;
import thunder.hack.setting.Setting;
import thunder.hack.setting.impl.Bind;
import thunder.hack.utility.player.InteractionUtility;
import thunder.hack.utility.player.InventoryUtility;
import thunder.hack.utility.player.PlayerUtility;
import thunder.hack.utility.player.SearchInvResult;
import thunder.hack.utility.render.Render2DEngine;
import thunder.hack.utility.render.Render3DEngine;

import java.awt.Color;

public class AutoCart extends Module {
    private static final Color CROSSBOW_DEBUG_FILL = new Color(255, 0, 0, 100);
    
    private final Setting<Mode> mode = new Setting<>("Mode", Mode.Bow);
    private final Setting<Float> maxDistance = new Setting<>("Max Distance", 4.5f, 2.0f, 6.0f, v -> mode.is(Mode.Bow));
    private final Setting<Integer> startDelay = new Setting<>("Start Delay", 25, 0, 60, v -> mode.is(Mode.Bow));
    private final Setting<Bind> activeBind = new Setting<>("Active Bind", new Bind(-1, false, false), v -> mode.is(Mode.CrossBow));
    private final Setting<Integer> delay = new Setting<>("Delay", 25, 0, 100, v -> mode.is(Mode.Bow) || mode.is(Mode.CrossBow));
    private final Setting<Integer> cartAuraDelay = new Setting<>("Cart Aura Delay", 0, 0, 20, v -> isCartAuraEnabled());
    private final Setting<Integer> refillSlot = new Setting<>("Slot", 9, 1, 9, v -> isRefillSlotVisible());
    private final Setting<Boolean> swapBack = new Setting<>("Swap Back", true);
    private final Setting<Boolean> changeLook = new Setting<>("Change Look", false);
    private final Setting<Boolean> cartAura = new Setting<>("Cart Aura", false, v -> mode.is(Mode.CrossBow));
    private final Setting<ReFillMode> reFill = new Setting<>("ReFill", ReFillMode.None);
    
    private volatile float[] silentRotation = null;
    private volatile boolean rotating = false;
    private boolean crossBowPressed = false;
    private volatile boolean cartAuraExecuting = false;
    private boolean scheduledRefillSwap = false;
    private int scheduledRefillInventorySlot = -1;
    private int scheduledRefillHotbarSlot = -1;
    private long scheduledRefillAt = -1L;

    public AutoCart() {
        super("AutoCart", Category.COMBAT);
    }

    private boolean isCartAuraEnabled() {
        return mode.is(Mode.CrossBow) && cartAura.getValue();
    }

    private boolean isRefillSlotVisible() {
        return reFill.getValue() != ReFillMode.None;
    }

    @Override
    public void onEnable() {
        resetState();
    }

    @Override
    public void onDisable() {
        resetState();
    }

    private void resetState() {
        silentRotation = null;
        rotating = false;
        crossBowPressed = false;
        cartAuraExecuting = false;
        clearRefillState();
    }

    @EventHandler
    public void onSync(EventSync e) {
        if (!fullNullCheck()) {
            if (rotating && silentRotation != null) {
                mc.player.setYaw(silentRotation[0]);
                mc.player.setPitch(silentRotation[1]);
            }
        }
    }

    @EventHandler
    public void onPacketSendPost(PacketEvent.SendPost event) {
        if (!fullNullCheck() && mode.is(Mode.Bow)) {
            if (event.getPacket() instanceof PlayerActionC2SPacket action && 
                action.getAction() == Action.RELEASE_USE_ITEM && 
                mc.player.getMainHandStack().getItem() == Items.BOW) {
                executeBowMode();
            }
        }
    }

    @Override
    public void onUpdate() {
        if (!fullNullCheck()) {
            handleScheduledRefill();
            handleRefill();
            if (mode.is(Mode.CrossBow)) {
                boolean pressed = activeBind.getValue().isKeyBindPressed();
                if (pressed && !crossBowPressed) {
                    crossBowPressed = true;
                    executeCrossBowMode();
                }
                if (!pressed) {
                    crossBowPressed = false;
                }
            }
        }
    }

    @Override
    public void onRender2D(DrawContext context) {
        if (!fullNullCheck() && mode.is(Mode.CrossBow) && !mc.options.hudHidden) {
            if (hasCrossbowDebugBaseRequirements()) {
                renderCrossbowDebug(context);
            }
        }
    }

    private void handleRefill() {
        if (reFill.is(ReFillMode.None)) {
            clearRefillState();
        } else if (!scheduledRefillSwap && mc.currentScreen == null && mc.player.currentScreenHandler == mc.player.playerScreenHandler) {
            int targetHotbarSlot = refillSlot.getValue() - 1;
            if (mc.player.getInventory().getStack(targetHotbarSlot).getItem() != Items.TNT_MINECART) {
                SearchInvResult cartResult = InventoryUtility.findItemInInventory(Items.TNT_MINECART);
                if (cartResult.found()) {
                    performRefillSwap(cartResult.slot(), targetHotbarSlot);
                }
            }
        }
    }

    private void handleScheduledRefill() {
        if (scheduledRefillSwap && System.currentTimeMillis() >= scheduledRefillAt) {
            try {
                performRefillSwap(scheduledRefillInventorySlot, scheduledRefillHotbarSlot);
            } finally {
                clearRefillState();
            }
        }
    }

    private void performRefillSwap(int inventorySlot, int hotbarSlot) {
        if (inventorySlot != -1 && hotbarSlot >= 0 && hotbarSlot <= 8) {
            if (mc.currentScreen == null && mc.player.currentScreenHandler == mc.player.playerScreenHandler) {
                if (mc.player.getInventory().getStack(hotbarSlot).getItem() != Items.TNT_MINECART) {
                    mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, inventorySlot, hotbarSlot, SlotActionType.SWAP, mc.player);
                }
            }
        }
    }

    private void clearRefillState() {
        scheduledRefillSwap = false;
        scheduledRefillInventorySlot = -1;
        scheduledRefillHotbarSlot = -1;
        scheduledRefillAt = -1L;
    }

    private void executeBowMode() {
        if (!fullNullCheck()) {
            SearchInvResult bowResult = InventoryUtility.findItemInHotBar(Items.BOW);
            SearchInvResult cartResult = InventoryUtility.findItemInHotBar(Items.TNT_MINECART);
            if (bowResult.found() && cartResult.found()) {
                BlockPos targetPos = calcBowTrajectory(mc.player.getYaw());
                if (targetPos != null) {
                    BlockPos basePos = getCartBasePos(targetPos);
                    boolean railExists = isRailBlock(mc.world.getBlockState(basePos.up()).getBlock());
                    if (railExists || findRailInHotBar().found()) {
                        float distSq = (float) mc.player.getEyePos().squaredDistanceTo(basePos.up().toCenterPos());
                        float maxDistSq = maxDistance.getValue() * maxDistance.getValue();
                        if (distSq <= maxDistSq && distSq >= 4.0f) {
                            AsyncManager.runTask(() -> executeBowPlacement(targetPos), startDelay.getValue());
                        }
                    }
                }
            }
        }
    }

    private void executeBowPlacement(@NotNull BlockPos targetPos) {
        if (!fullNullCheck() && mode.is(Mode.Bow)) {
            BlockPos basePos = getCartBasePos(targetPos);
            boolean railExists = isRailBlock(mc.world.getBlockState(basePos.up()).getBlock());
            SearchInvResult railResult = findRailInHotBar();
            SearchInvResult cartResult = InventoryUtility.findItemInHotBar(Items.TNT_MINECART);
            if (cartResult.found() && (railExists || railResult.found())) {
                int prevSlot = mc.player.getInventory().selectedSlot;
                int delayMs = delay.getValue();
                Vec3d placeVec = new Vec3d(basePos.getX() + 0.5, basePos.up().getY(), basePos.getZ() + 0.5);
                mc.execute(() -> applyRotation(InteractionUtility.calculateAngle(placeVec)));
                
                try { Thread.sleep(delayMs); } catch (InterruptedException ignored) {}
                
                if (!railExists) {
                    mc.execute(() -> {
                        if (!isRailBlock(mc.world.getBlockState(basePos.up()).getBlock())) {
                            selectHotbarLegit(railResult.slot());
                            placeRailOn(basePos);
                        }
                    });
                    try { Thread.sleep(delayMs); } catch (InterruptedException ignored) {}
                }
                
                mc.execute(() -> {
                    selectHotbarLegit(cartResult.slot());
                    placeMinecartOn(basePos);
                });
                try { Thread.sleep(delayMs); } catch (InterruptedException ignored) {}
                
                mc.execute(() -> {
                    if (swapBack.getValue()) {
                        selectHotbarLegit(prevSlot);
                    }
                    endRotation();
                });
            }
        }
    }

    @NotNull
    private BlockPos getCartBasePos(@NotNull BlockPos targetPos) {
        BlockState targetState = mc.world.getBlockState(targetPos);
        return !isRailBlock(targetState.getBlock()) && !targetState.isAir() ? targetPos : targetPos.down();
    }

    private void executeCrossBowMode() {
        if (!fullNullCheck()) {
            SearchInvResult crossbowResult = findLoadedCrossbowInHotBar();
            SearchInvResult railResult = findRailInHotBar();
            SearchInvResult cartResult = InventoryUtility.findItemInHotBar(Items.TNT_MINECART);
            if (crossbowResult.found() && railResult.found() && cartResult.found()) {
                boolean hasFlame = hasFlameEnchant(mc.player.getInventory().getStack(crossbowResult.slot()));
                SearchInvResult flintResult = InventoryUtility.findItemInHotBar(Items.FLINT_AND_STEEL);
                if (hasFlame || flintResult.found()) {
                    BlockHitResult rayResult = rayTraceFromEyes(4.5);
                    if (rayResult != null && rayResult.getType() == HitResult.Type.BLOCK) {
                        BlockPos hitPos = rayResult.getBlockPos();
                        if (mc.player.getEyePos().squaredDistanceTo(hitPos.toCenterPos()) <= 20.25f) {
                            BlockPos basePos = mc.world.getBlockState(hitPos).isAir() ? hitPos.down() : hitPos;
                            BlockPos fireBlockPos = null;
                            if (!hasFlame) {
                                fireBlockPos = findFirePosition(basePos);
                                if (fireBlockPos == null) return;
                            }
                            BlockPos finalFireBlockPos = fireBlockPos;
                            int prevSlot = mc.player.getInventory().selectedSlot;
                            int delayMs = delay.getValue();
                            
                            AsyncManager.runTask(() -> {
                                Vec3d placeVec = new Vec3d(basePos.getX() + 0.5, basePos.up().getY(), basePos.getZ() + 0.5);
                                mc.execute(() -> applyRotation(InteractionUtility.calculateAngle(placeVec)));
                                try { Thread.sleep(delayMs); } catch (InterruptedException ignored) {}
                                
                                mc.execute(() -> {
                                    if (!isRailBlock(mc.world.getBlockState(basePos.up()).getBlock())) {
                                        selectHotbarLegit(railResult.slot());
                                        placeRailOn(basePos);
                                    }
                                });
                                try { Thread.sleep(delayMs); } catch (InterruptedException ignored) {}
                                
                                mc.execute(() -> {
                                    selectHotbarLegit(cartResult.slot());
                                    placeMinecartOn(basePos);
                                });
                                try { Thread.sleep(delayMs); } catch (InterruptedException ignored) {}
                                
                                if (!hasFlame && finalFireBlockPos != null) {
                                    mc.execute(() -> {
                                        Vec3d fireVec = new Vec3d(finalFireBlockPos.getX() + 0.5, finalFireBlockPos.getY() + 1.0, finalFireBlockPos.getZ() + 0.5);
                                        applyRotation(InteractionUtility.calculateAngle(fireVec));
                                        selectHotbarLegit(flintResult.slot());
                                        BlockHitResult fireHit = new BlockHitResult(fireVec, Direction.UP, finalFireBlockPos, false);
                                        interactWithBlock(fireHit);
                                    });
                                    try { Thread.sleep(delayMs); } catch (InterruptedException ignored) {}
                                }
                                
                                mc.execute(() -> {
                                    Vec3d minecartCenter = new Vec3d(basePos.getX() + 0.5, basePos.getY() + 1.5, basePos.getZ() + 0.5);
                                    float[] shootAngle = InteractionUtility.calculateAngle(minecartCenter);
                                    applyRotation(shootAngle);
                                    selectHotbarLegit(crossbowResult.slot());
                                    interactWithItem();
                                    mc.player.swingHand(Hand.MAIN_HAND);
                                });
                                try { Thread.sleep(delayMs); } catch (InterruptedException ignored) {}
                                
                                mc.execute(() -> {
                                    if (swapBack.getValue()) {
                                        selectHotbarLegit(prevSlot);
                                    }
                                    endRotation();
                                });
                            });
                        }
                    }
                }
            }
        }
    }

    private void applyRotation(float[] angle) {
        if (changeLook.getValue()) {
            mc.player.setYaw(angle[0]);
            mc.player.setPitch(angle[1]);
        } else {
            silentRotation = angle;
            rotating = true;
        }
    }

    private void endRotation() {
        silentRotation = null;
        rotating = false;
    }

    private void selectHotbarLegit(int slot) {
        if (mc.player != null) {
            mc.player.getInventory().selectedSlot = slot;
        }
    }

    private void interactWithItem() {
        if (mc.player != null && mc.interactionManager != null) {
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        }
    }

    private void placeRailOn(BlockPos basePos) {
        if (mc.world != null && mc.player != null && mc.interactionManager != null) {
            BlockHitResult hitResult = new BlockHitResult(new Vec3d(basePos.getX() + 0.5, basePos.up().getY(), basePos.getZ() + 0.5), Direction.UP, basePos, false);
            interactWithBlock(hitResult);
        }
    }

    private void placeMinecartOn(BlockPos basePos) {
        if (mc.world != null && mc.player != null && mc.interactionManager != null) {
            BlockHitResult hitResult = new BlockHitResult(new Vec3d(basePos.getX() + 0.5, basePos.up().getY() + 0.125, basePos.getZ() + 0.5), Direction.UP, basePos.up(), false);
            interactWithBlock(hitResult);
        }
    }

    private void interactWithBlock(BlockHitResult hitResult) {
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitResult);
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    @Nullable
    private BlockPos calcBowTrajectory(float yaw) {
        if (mc.player != null && mc.world != null) {
            double x = Render2DEngine.interpolate(mc.player.prevX, mc.player.getX(), Render3DEngine.getTickDelta());
            double y = Render2DEngine.interpolate(mc.player.prevY, mc.player.getY(), Render3DEngine.getTickDelta());
            double z = Render2DEngine.interpolate(mc.player.prevZ, mc.player.getZ(), Render3DEngine.getTickDelta());
            y += mc.player.getEyeHeight(mc.player.getPose()) - 0.1;
            float pitch = mc.player.getPitch();
            double motionX = -MathHelper.sin(yaw * MathHelper.RADIANS_PER_DEGREE) * MathHelper.cos(pitch * MathHelper.RADIANS_PER_DEGREE);
            double motionY = -MathHelper.sin(pitch * MathHelper.RADIANS_PER_DEGREE);
            double motionZ = MathHelper.cos(yaw * MathHelper.RADIANS_PER_DEGREE) * MathHelper.cos(pitch * MathHelper.RADIANS_PER_DEGREE);
            float power = mc.player.getItemUseTime() / 20.0f;
            power = (power * power + power * 2.0f) / 3.0f;
            if (power > 1.0f) power = 1.0f;
            if (power < 0.1f) return null;
            float dist = MathHelper.sqrt((float) (motionX * motionX + motionY * motionY + motionZ * motionZ));
            motionX /= dist;
            motionY /= dist;
            motionZ /= dist;
            float pow = power * 3.0f;
            motionX *= pow;
            motionY *= pow;
            motionZ *= pow;
            
            for (int i = 0; i < 300; i++) {
                Vec3d lastPos = new Vec3d(x, y, z);
                x += motionX;
                y += motionY;
                z += motionZ;
                motionX *= 0.99;
                motionY *= 0.99;
                motionZ *= 0.99;
                motionY -= 0.05;
                
                for (Entity ent : mc.world.getEntities()) {
                    if (!(ent instanceof ArrowEntity) && !ent.equals(mc.player) && 
                        ent.getBoundingBox().intersects(new Box(x - 0.3, y - 0.3, z - 0.3, x + 0.3, y + 0.3, z + 0.3))) {
                        return null;
                    }
                }
                
                Vec3d pos = new Vec3d(x, y, z);
                BlockHitResult bhr = mc.world.raycast(new RaycastContext(lastPos, pos, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, mc.player));
                if (bhr != null && bhr.getType() == HitResult.Type.BLOCK) {
                    return bhr.getBlockPos();
                }
                if (y <= -65.0) break;
            }
            return null;
        }
        return null;
    }

    private SearchInvResult findRailInHotBar() {
        return InventoryUtility.findItemInHotBar(Items.RAIL, Items.ACTIVATOR_RAIL, Items.DETECTOR_RAIL, Items.POWERED_RAIL);
    }

    private void renderCrossbowDebug(DrawContext context) {
        int hotbarLeft = mc.getWindow().getScaledWidth() / 2 - 91;
        int hotbarTop = mc.getWindow().getScaledHeight() - 22;
        
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (isCrossbowDebugCandidate(stack)) {
                Render2DEngine.drawRect(context.getMatrices(), hotbarLeft + slot * 20 + 1, hotbarTop + 1, 20, 20, CROSSBOW_DEBUG_FILL);
            }
        }
    }

    private boolean hasCrossbowDebugBaseRequirements() {
        if (findRailInHotBar().found() && InventoryUtility.findItemInHotBar(Items.TNT_MINECART).found()) {
            if (!hasArrowAmmoInInventory()) return false;
            for (int slot = 0; slot < 9; slot++) {
                if (isCrossbowDebugCandidate(mc.player.getInventory().getStack(slot))) {
                    return true;
                }
            }
            return false;
        }
        return false;
    }

    private boolean isCrossbowDebugCandidate(ItemStack stack) {
        return !isUnloadedCrossbow(stack) ? false : hasFlameEnchant(stack) || InventoryUtility.findItemInHotBar(Items.FLINT_AND_STEEL).found();
    }

    private boolean hasArrowAmmoInInventory() {
        for (ItemStack stack : mc.player.getInventory().main) {
            if (isArrowStack(stack)) return true;
        }
        for (ItemStack stack : mc.player.getInventory().offHand) {
            if (isArrowStack(stack)) return true;
        }
        return false;
    }

    private boolean isArrowStack(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof ArrowItem;
    }

    private boolean isUnloadedCrossbow(ItemStack stack) {
        return stack.getItem() == Items.CROSSBOW && !isCrossbowCharged(stack);
    }

    private boolean isCrossbowCharged(ItemStack stack) {
        return stack.get(DataComponentTypes.CHARGED_PROJECTILES) != null && !stack.get(DataComponentTypes.CHARGED_PROJECTILES).isEmpty();
    }

    private SearchInvResult findLoadedCrossbowInHotBar() {
        return InventoryUtility.findInHotBar(this::isCrossbowCharged);
    }

    private boolean isRailBlock(Block block) {
        return block == Blocks.RAIL || block == Blocks.POWERED_RAIL || block == Blocks.DETECTOR_RAIL || block == Blocks.ACTIVATOR_RAIL;
    }

    private boolean hasFlameEnchant(ItemStack stack) {
        return EnchantmentHelper.getLevel(Enchantments.FLAME, stack) > 0;
    }

    @Nullable
    private BlockHitResult rayTraceFromEyes(double maxRange) {
        if (mc.player != null && mc.world != null) {
            Vec3d eyePos = mc.player.getEyePos();
            Vec3d lookVec = mc.player.getRotationVec(1.0f);
            Vec3d endPos = eyePos.add(lookVec.multiply(maxRange));
            return mc.world.raycast(new RaycastContext(eyePos, endPos, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, mc.player));
        }
        return null;
    }

    @Nullable
    private BlockPos findFirePosition(BlockPos targetPos) {
        if (mc.player != null && mc.world != null) {
            Vec3d eyePos = mc.player.getEyePos();
            Vec3d minecartPos = new Vec3d(targetPos.getX() + 0.5, targetPos.getY() + 1.5, targetPos.getZ() + 0.5);
            Vec3d dir = minecartPos.subtract(eyePos).normalize();
            Direction[] horizontals = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
            
            for (Direction d : horizontals) {
                BlockPos candidate = targetPos.offset(d);
                if (mc.world.getBlockState(candidate).isSolid() && mc.world.isAir(candidate.up())) {
                    return candidate;
                }
            }
            return targetPos;
        }
        return null;
    }

    @Override
    public String getDisplayInfo() {
        return mode.getValue().name();
    }

    public enum Mode { Bow, CrossBow }
    public enum ReFillMode { None, Normal, Legit }
}