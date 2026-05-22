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
import thunder.hack.core.manager.client.ModuleManager;
import thunder.hack.events.impl.EventFixVelocity;
import thunder.hack.events.impl.EventKeyboardInput;
import thunder.hack.events.impl.EventSync;
import thunder.hack.events.impl.PacketEvent;
import thunder.hack.events.impl.TotemPopEvent;
import thunder.hack.features.modules.Module;
import thunder.hack.setting.Setting;
import thunder.hack.setting.impl.Bind;
import thunder.hack.setting.impl.SettingGroup;
import thunder.hack.utility.player.InteractionUtility;
import thunder.hack.utility.player.InventoryUtility;
import thunder.hack.utility.player.MovementUtility;
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
    private final Setting<SettingGroup> cartAuraTargets = new Setting<>("Target", new SettingGroup(false, 0), v -> mode.is(Mode.CrossBow) && cartAura.getValue());
    private final Setting<Boolean> cartAuraTarget = new Setting<>("Aura Target", true, v -> mode.is(Mode.CrossBow) && cartAura.getValue()).addToGroup(cartAuraTargets);
    private final Setting<Boolean> cartOtherPlayer = new Setting<>("Other Player", false, v -> mode.is(Mode.CrossBow) && cartAura.getValue()).addToGroup(cartAuraTargets);
    
    private volatile float[] silentRotation = null;
    private volatile boolean rotating = false;
    private boolean crossBowPressed = false;
    private volatile boolean cartAuraExecuting = false;
    private boolean scheduledRefillSwap = false;
    private int scheduledRefillInventorySlot = -1;
    private int scheduledRefillHotbarSlot = -1;
    private long scheduledRefillAt = -1L;

    public AutoCart() {
        super("AutoCart", "Automates TNT minecart combat setups.", Category.COMBAT);
    }

    private boolean isCartAuraEnabled() {
        return mode.is(Mode.CrossBow) && cartAura != null && cartAura.getValue();
    }

    private boolean isRefillSlotVisible() {
        return reFill != null && reFill.getValue() != ReFillMode.None;
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
        if (cartAuraExecuting) {
            ModuleManager.aura.externalPause = false;
        }
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

    @EventHandler(priority = -200)
    public void onPlayerMove(EventFixVelocity event) {
        float[] rotation = getAutoCartMoveFixRotation();
        if (rotation != null) {
            event.setVelocity(fixMovement(rotation[0], event.getMovementInput(), event.getSpeed()));
        }
    }

    @EventHandler(priority = -200)
    public void onKeyboardInput(EventKeyboardInput event) {
        float[] rotation = getAutoCartMoveFixRotation();
        if (rotation != null) {
            float moveForward = mc.player.input.movementForward;
            float moveSideways = mc.player.input.movementSideways;
            float delta = (mc.player.getYaw() - rotation[0]) * (float) (Math.PI / 180.0);
            float cos = MathHelper.cos(delta);
            float sin = MathHelper.sin(delta);
            mc.player.input.movementSideways = Math.round(moveSideways * cos - moveForward * sin);
            mc.player.input.movementForward = Math.round(moveForward * cos + moveSideways * sin);
        }
    }

    @EventHandler
    public void onPacketSendPost(PacketEvent.SendPost event) {
        if (!fullNullCheck() && mode.is(Mode.Bow)) {
            if (event.getPacket() instanceof PlayerActionC2SPacket action && action.getAction() == Action.RELEASE_USE_ITEM && mc.player.getMainHandStack().getItem() == Items.BOW) {
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
                boolean pressed = isKeyPressed(activeBind.getValue().getCode());
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

    private void scheduleRefillSwap(int inventorySlot, int hotbarSlot, long delayMs) {
        if (inventorySlot == -1) {
            clearRefillState();
        } else {
            scheduledRefillSwap = true;
            scheduledRefillInventorySlot = inventorySlot;
            scheduledRefillHotbarSlot = hotbarSlot;
            scheduledRefillAt = System.currentTimeMillis() + delayMs;
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

    @EventHandler
    public void onTotemPop(@NotNull TotemPopEvent event) {
        if (!fullNullCheck() && mode.is(Mode.CrossBow) && cartAura.getValue()) {
            if (!cartAuraExecuting) {
                PlayerEntity popTarget = event.getEntity();
                if (popTarget != mc.player) {
                    boolean isAuraTarget = cartAuraTarget.getValue() && ModuleManager.aura.target != null && popTarget == ModuleManager.aura.target;
                    boolean isOtherPlayer = cartOtherPlayer.getValue() && !Managers.FRIEND.isFriend(popTarget) && mc.player.distanceTo(popTarget) <= 6.0;
                    if (isAuraTarget || isOtherPlayer) {
                        BlockPos placePos = findCartAuraPosition(popTarget);
                        if (placePos != null) {
                            SearchInvResult crossbowResult = findLoadedCrossbowInHotBar();
                            SearchInvResult cartResult = InventoryUtility.findItemInHotBar(Items.TNT_MINECART);
                            if (crossbowResult.found() && cartResult.found()) {
                                boolean hasFlame = hasFlameEnchant(mc.player.getInventory().getStack(crossbowResult.slot()));
                                SearchInvResult flintResult = InventoryUtility.findItemInHotBar(Items.FLINT_AND_STEEL);
                                if (hasFlame || flintResult.found()) {
                                    boolean railExists = isRailBlock(mc.world.getBlockState(placePos.up()).getBlock());
                                    SearchInvResult railResult = findRailInHotBar();
                                    if (railExists || railResult.found()) {
                                        executeCartAura(placePos, crossbowResult, railResult, cartResult, flintResult, hasFlame, railExists);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void executeCartAura(BlockPos basePos, SearchInvResult crossbowResult, SearchInvResult railResult, SearchInvResult cartResult, SearchInvResult flintResult, boolean hasFlame, boolean railExists) {
        cartAuraExecuting = true;
        int prevSlot = mc.player.getInventory().selectedSlot;
        int delayMs = delay.getValue();
        int startDelayMs = cartAuraDelay.getValue() * 50;
        
        AsyncManager.runAsync(() -> {
            try {
                if (startDelayMs > 0) {
                    Thread.sleep(startDelayMs);
                }
                if (!fullNullCheck() && !isDisabled() && mode.is(Mode.CrossBow) && cartAura.getValue()) {
                    ModuleManager.aura.externalPause = true;
                    Vec3d placeVec = new Vec3d(basePos.getX() + 0.5, basePos.up().getY(), basePos.getZ() + 0.5);
                    mc.execute(() -> applyRotation(InteractionUtility.calculateAngle(placeVec)));
                    Thread.sleep(delayMs);
                    
                    if (!railExists) {
                        mc.execute(() -> {
                            if (!isRailBlock(mc.world.getBlockState(basePos.up()).getBlock())) {
                                selectHotbarLegit(railResult.slot());
                                placeRailOn(basePos);
                            }
                        });
                        Thread.sleep(delayMs);
                    }
                    
                    mc.execute(() -> {
                        selectHotbarLegit(cartResult.slot());
                        placeMinecartOn(basePos);
                    });
                    Thread.sleep(delayMs);
                    
                    if (!hasFlame) {
                        BlockPos firePos = findFirePosition(basePos);
                        if (firePos != null) {
                            mc.execute(() -> {
                                Vec3d fireVec = new Vec3d(firePos.getX() + 0.5, firePos.getY() + 1.0, firePos.getZ() + 0.5);
                                applyRotation(InteractionUtility.calculateAngle(fireVec));
                                selectHotbarLegit(flintResult.slot());
                                BlockHitResult fireHit = new BlockHitResult(fireVec, Direction.UP, firePos, false);
                                interactWithBlock(fireHit);
                            });
                            Thread.sleep(delayMs);
                        }
                    }
                    
                    mc.execute(() -> {
                        Vec3d minecartCenter = new Vec3d(basePos.getX() + 0.5, basePos.getY() + 1.5, basePos.getZ() + 0.5);
                        float[] shootAngle = InteractionUtility.calculateAngle(minecartCenter);
                        applyRotation(shootAngle);
                        selectHotbarLegit(crossbowResult.slot());
                        interactWithItem();
                        mc.player.swingHand(Hand.MAIN_HAND);
                    });
                    Thread.sleep(delayMs);
                    
                    mc.execute(() -> {
                        if (swapBack.getValue()) {
                            selectHotbarLegit(prevSlot);
                        }
                        endRotation();
                    });
                }
            } catch (InterruptedException ignored) {
            } finally {
                ModuleManager.aura.externalPause = false;
                cartAuraExecuting = false;
            }
        });
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
                        float distSq = (float) PlayerUtility.squaredDistanceFromEyes(basePos.up().toCenterPos());
                        float maxDistSq = maxDistance.getValue() * maxDistance.getValue();
                        float safeDistSq = 4.0f;
                        if (!(distSq > maxDistSq) && !(distSq < safeDistSq)) {
                            AsyncManager.runAsync(() -> executeBowPlacement(targetPos), startDelay.getValue());
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
                        if (!(PlayerUtility.squaredDistanceFromEyes(hitPos.toCenterPos()) > 20.25f)) {
                            BlockPos basePos = mc.world.getBlockState(hitPos).isAir() ? hitPos.down() : hitPos;
                            BlockPos fireBlockPos = null;
                            if (!hasFlame) {
                                fireBlockPos = findFirePosition(basePos);
                                if (fireBlockPos == null) return;
                            }
                            BlockPos finalFireBlockPos = fireBlockPos;
                            int prevSlot = mc.player.getInventory().selectedSlot;
                            int delayMs = delay.getValue();
                            
                            AsyncManager.runAsync(() -> {
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

    public boolean isAutoCartMoveFixActive() {
        return getAutoCartMoveFixRotation() != null;
    }

    @Nullable
    private float[] getAutoCartMoveFixRotation() {
        float[] rotation = silentRotation;
        return isOn() && !fullNullCheck() && !changeLook.getValue() && rotating && rotation != null && !mc.player.isRiding() ? rotation : null;
    }

    private Vec3d fixMovement(float yaw, Vec3d movementInput, float speed) {
        double lengthSquared = movementInput.lengthSquared();
        if (lengthSquared < 1.0E-7) {
            return Vec3d.ZERO;
        }
        Vec3d movement = (lengthSquared > 1.0 ? movementInput.normalize() : movementInput).multiply(speed);
        float sin = MathHelper.sin(yaw * (float) (Math.PI / 180.0));
        float cos = MathHelper.cos(yaw * (float) (Math.PI / 180.0));
        return new Vec3d(movement.x * cos - movement.z * sin, movement.y, movement.z * cos + movement.x * sin);
    }

    private void selectHotbarLegit(int slot) {
        if (mc.player != null) {
            mc.player.getInventory().selectedSlot = slot;
        }
    }

    private void runWithInteractionRotation(@Nullable float[] angle, Runnable action) {
        if (mc.player != null) {
            if (!changeLook.getValue() && angle != null) {
                float prevYaw = mc.player.getYaw();
                float prevPitch = mc.player.getPitch();
                mc.player.setYaw(angle[0]);
                mc.player.setPitch(angle[1]);
                try {
                    action.run();
                } finally {
                    mc.player.setYaw(prevYaw);
                    mc.player.setPitch(prevPitch);
                }
            } else {
                action.run();
            }
        }
    }

    private void interactWithItem() {
        if (mc.player != null && mc.interactionManager != null) {
            runWithInteractionRotation(silentRotation, () -> mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND));
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
        runWithInteractionRotation(silentRotation, () -> mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitResult));
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    @Nullable
    private BlockPos calcBowTrajectory(float yaw) {
        if (mc.player != null && mc.world != null) {
            double x = Render2DEngine.interpolate(mc.player.prevX, mc.player.getX(), Render3DEngine.getTickDelta());
            double y = Render2DEngine.interpolate(mc.player.prevY, mc.player.getY(), Render3DEngine.getTickDelta());
            double z = Render2DEngine.interpolate(mc.player.prevZ, mc.player.getZ(), Render3DEngine.getTickDelta());
            y += mc.player.getEyeHeight(mc.player.getPose()) - 0.1000000014901161;
            float pitch = mc.player.getPitch();
            double motionX = -MathHelper.sin(yaw / 180.0f * (float) Math.PI) * MathHelper.cos(pitch / 180.0f * (float) Math.PI);
            double motionY = -MathHelper.sin(pitch / 180.0f * (float) Math.PI);
            double motionZ = MathHelper.cos(yaw / 180.0f * (float) Math.PI) * MathHelper.cos(pitch / 180.0f * (float) Math.PI);
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
            if (!mc.player.isSubmergedInWater()) {
                motionY += mc.player.getWorld().getDimension().hasCeiling();
            }
            for (int i = 0; i < 300; i++) {
                Vec3d lastPos = new Vec3d(x, y, z);
                x += motionX;
                y += motionY;
                z += motionZ;
                motionX *= 0.99;
                motionY *= 0.99;
                motionZ *= 0.99;
                motionY -= 0.05f;
                
                for (Entity ent : mc.world.getEntities()) {
                    if (!(ent instanceof ArrowEntity) && !ent.equals(mc.player) && ent.getBoundingBox().intersects(new Box(x - 0.3, y - 0.3, z - 0.3, x + 0.3, y + 0.3, z + 0.3))) {
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

    @Nullable
    private BlockPos findCartAuraPosition(Entity target) {
        if (mc.player != null && mc.world != null) {
            Vec3d playerEyes = InteractionUtility.getEyesPos(mc.player);
            Box targetBox = target.getBoundingBox();
            Vec3d targetFeet = new Vec3d(target.getX(), targetBox.minY, target.getZ());
            BlockPos targetBlockPos = target.getBlockPos();
            int r = 4;
            BlockPos bestPos = null;
            double bestDist = Double.MAX_VALUE;
            
            for (int x = targetBlockPos.getX() - r; x <= targetBlockPos.getX() + r; x++) {
                for (int z = targetBlockPos.getZ() - r; z <= targetBlockPos.getZ() + r; z++) {
                    for (int y = targetBlockPos.getY() - r; y <= targetBlockPos.getY(); y++) {
                        BlockPos bp = new BlockPos(x, y, z);
                        if (mc.world.getBlockState(bp).isSolid() && !(bp.getY() + 1 > targetBox.minY)) {
                            BlockState aboveState = mc.world.getBlockState(bp.up());
                            if (aboveState.isAir() || isRailBlock(aboveState.getBlock())) {
                                Vec3d surfacePos = new Vec3d(bp.getX() + 0.5, bp.getY() + 1.0, bp.getZ() + 0.5);
                                if (!(PlayerUtility.squaredDistanceFromEyes(surfacePos) > 20.25f)) {
                                    BlockHitResult blockCheck = mc.world.raycast(new RaycastContext(playerEyes, surfacePos, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
                                    if ((blockCheck == null || blockCheck.getType() != HitResult.Type.BLOCK || blockCheck.getBlockPos().equals(bp)) && isPathClearOfPlayers(playerEyes, surfacePos, target)) {
                                        Vec3d cartCenter = new Vec3d(bp.getX() + 0.5, bp.getY() + 1.5, bp.getZ() + 0.5);
                                        BlockHitResult damageCheck = mc.world.raycast(new RaycastContext(cartCenter, targetFeet, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
                                        if (damageCheck == null || damageCheck.getType() != HitResult.Type.BLOCK) {
                                            double distToTarget = cartCenter.squaredDistanceTo(targetFeet);
                                            if (distToTarget < bestDist) {
                                                bestDist = distToTarget;
                                                bestPos = bp;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            return bestPos;
        }
        return null;
    }

    private boolean isPathClearOfPlayers(Vec3d from, Vec3d to, Entity exclude) {
        if (mc.world == null) return true;
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player != mc.player && player != exclude && player.getBoundingBox().raycast(from, to).isPresent()) {
                return false;
            }
        }
        return true;
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
                float slotX = hotbarLeft + slot * 20 + 1;
                float slotY = hotbarTop + 1;
                Render2DEngine.drawRect(context.getMatrices(), slotX, slotY, 20.0f, 20.0f, CROSSBOW_DEBUG_FILL);
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
        return stack.getItem() == Items.CROSSBOW && stack.get(DataComponentTypes.CHARGED_PROJECTILES) != null && !stack.get(DataComponentTypes.CHARGED_PROJECTILES).isEmpty();
    }

    private SearchInvResult findLoadedCrossbowInHotBar() {
        return InventoryUtility.findInHotBar(this::isCrossbowCharged);
    }

    private boolean isRailBlock(Block block) {
        return block == Blocks.RAIL || block == Blocks.POWERED_RAIL || block == Blocks.DETECTOR_RAIL || block == Blocks.ACTIVATOR_RAIL;
    }

    private boolean hasFlameEnchant(ItemStack stack) {
        if (mc.world == null) return false;
        RegistryEntry<Enchantments> flame = mc.world.getRegistryManager().get(Enchantments.ENCHANTMENT_KEY).getEntry(Enchantments.FLAME).get();
        return EnchantmentHelper.getLevel(flame, stack) > 0;
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
            BlockPos bestPos = null;
            double bestScore = Double.MAX_VALUE;
            
            for (Direction d : horizontals) {
                BlockPos candidate = targetPos.offset(d);
                if (mc.world.getBlockState(candidate).isSolid() && mc.world.isAir(candidate.up())) {
                    Vec3d fireCenter = new Vec3d(candidate.getX() + 0.5, candidate.getY() + 1.0, candidate.getZ() + 0.5);
                    Vec3d eyeToFire = fireCenter.subtract(eyePos);
                    double t = eyeToFire.dotProduct(dir);
                    if (t > 0) {
                        double eyeToMinecart = minecartPos.subtract(eyePos).dotProduct(dir);
                        if (t < eyeToMinecart) {
                            Vec3d closest = eyePos.add(dir.multiply(t));
                            double distToLine = closest.distanceTo(fireCenter);
                            if (distToLine < bestScore) {
                                bestScore = distToLine;
                                bestPos = candidate;
                            }
                        }
                    }
                }
            }
            return bestPos != null ? bestPos : targetPos;
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