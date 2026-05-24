package thunder.hack.features.modules.combat;

import java.awt.Color;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.class_1268;
import net.minecraft.class_1297;
import net.minecraft.class_1657;
import net.minecraft.class_1667;
import net.minecraft.class_1713;
import net.minecraft.class_1744;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_1890;
import net.minecraft.class_1893;
import net.minecraft.class_2246;
import net.minecraft.class_2248;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_238;
import net.minecraft.class_239.class_240;
import net.minecraft.class_243;
import net.minecraft.class_2680;
import net.minecraft.class_2846;
import net.minecraft.class_2846.class_2847;
import net.minecraft.class_332;
import net.minecraft.class_3532;
import net.minecraft.class_3959;
import net.minecraft.class_3959.class_242;
import net.minecraft.class_3959.class_3960;
import net.minecraft.class_3965;
import net.minecraft.class_6880;
import net.minecraft.class_9334;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import thunder.hack.core.InputBlocker;
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

public class AutoCart extends Module {
   private static final Color CROSSBOW_DEBUG_FILL = new Color(255, 0, 0, 100);
   private static final int HOTBAR_SLOT_SIZE = 20;
   private static final int HOTBAR_Y_OFFSET = 22;
   private static final int HOTBAR_LEFT_OFFSET = 91;
   private static final int TICK_LENGTH_MS = 50;

   private final Setting<AutoCart.Mode> mode = new Setting<>("Mode", AutoCart.Mode.Bow);
   private final Setting<Float> maxDistance = new Setting<>("MaxRange", 4.5F, 2.0F, 6.0F, v -> this.mode.is(AutoCart.Mode.Bow));
   private final Setting<Integer> startDelay = new Setting<>("BowDelay", 25, 0, 60, v -> this.mode.is(AutoCart.Mode.Bow));
   private final Setting<Bind> activeBind = new Setting<>("Keybind", new Bind(-1, false, false), v -> this.mode.is(AutoCart.Mode.CrossBow));
   private final Setting<Integer> delay = new Setting<>("FireDelay", 25, 0, 100, v -> this.mode.is(AutoCart.Mode.Bow) || this.mode.is(AutoCart.Mode.CrossBow));
   private final Setting<Integer> cartAuraDelay = new Setting<>("AuraDelay", 0, 0, 20, v -> this.isCartAuraEnabled());
   private final Setting<Integer> refillSlot = new Setting<>("RefillSlot", 9, 1, 9, v -> this.isRefillSlotVisible());
   private final Setting<Boolean> swapBack = new Setting<>("ReturnSlot", true);
   private final Setting<Boolean> changeLook = new Setting<>("RealRotate", false);
   private final Setting<Boolean> cartAura = new Setting<>("CartAura", false, v -> this.mode.is(AutoCart.Mode.CrossBow));
   private final Setting<Boolean> bowAura = new Setting<>("BowAura", false, v -> this.mode.is(AutoCart.Mode.Bow));
   private final Setting<AutoCart.ReFillMode> reFill = new Setting<>("AutoRefill", AutoCart.ReFillMode.None);
   private final Setting<Boolean> killTarget = new Setting<>("KillTarget", true);
   private final Setting<Boolean> otherPlayers = new Setting<>("NearbyPlayers", false);

   private volatile float[] silentRotation = null;
   private volatile boolean rotating = false;
   private boolean crossBowPressed = false;
   private volatile boolean cartAuraExecuting = false;
   private boolean scheduledRefillSwap = false;
   private int scheduledRefillInventorySlot = -1;
   private int scheduledRefillHotbarSlot = -1;
   private long scheduledRefillAt = -1L;
   private static final String REFILL_BLOCK_OWNER = "autocart_refill";
   private static final long LEGIT_REFILL_DELAY_MS = 5L;

   public AutoCart() {
      super("AutoCart", Module.Category.COMBAT);
   }

   private boolean isCartAuraEnabled() {
      return this.mode.is(AutoCart.Mode.CrossBow) && this.cartAura != null && this.cartAura.getValue();
   }

   private boolean isRefillSlotVisible() {
      return this.reFill != null && this.reFill.getValue() != AutoCart.ReFillMode.None;
   }

   @Override
   public void onEnable() {
      this.resetState();
   }

   @Override
   public void onDisable() {
      this.resetState();
   }

   private void resetState() {
      this.silentRotation = null;
      this.rotating = false;
      this.crossBowPressed = false;
      if (this.cartAuraExecuting) {
         ModuleManager.aura.externalPause = false;
      }
      this.cartAuraExecuting = false;
      this.clearRefillState();
   }

   @EventHandler
   public void onSync(EventSync e) {
      if (!fullNullCheck()) {
         if (this.rotating && this.silentRotation != null) {
            mc.field_1724.method_36456(this.silentRotation[0]);
            mc.field_1724.method_36457(this.silentRotation[1]);
         }
      }
   }

   @EventHandler(priority = -200)
   public void onPlayerMove(EventFixVelocity event) {
      float[] rotation = this.getAutoCartMoveFixRotation();
      if (rotation != null) {
         event.setVelocity(this.fixMovement(rotation[0], event.getMovementInput(), event.getSpeed()));
      }
   }

   @EventHandler(priority = -200)
   public void onKeyboardInput(EventKeyboardInput event) {
      float[] rotation = this.getAutoCartMoveFixRotation();
      if (rotation != null) {
         float moveForward = mc.field_1724.field_3913.field_3905;
         float moveSideways = mc.field_1724.field_3913.field_3907;
         float delta = (mc.field_1724.method_36454() - rotation[0]) * (float) (Math.PI / 180.0);
         float cos = class_3532.method_15362(delta);
         float sin = class_3532.method_15374(delta);
         mc.field_1724.field_3913.field_3907 = Math.round(moveSideways * cos - moveForward * sin);
         mc.field_1724.field_3913.field_3905 = Math.round(moveForward * cos + moveSideways * sin);
      }
   }

   @EventHandler
   public void onPacketSendPost(PacketEvent.@NotNull SendPost event) {
      if (!fullNullCheck() && this.mode.is(AutoCart.Mode.Bow)) {
         if (event.getPacket() instanceof class_2846 action
            && action.method_12363() == class_2847.field_12974
            && mc.field_1724.method_6047().method_7909() == class_1802.field_8102) {
            this.executeBowMode();
         }
      }
   }

   @Override
   public void onUpdate() {
      if (!fullNullCheck()) {
         this.handleScheduledRefill();
         this.handleRefill();
         if (this.mode.is(AutoCart.Mode.CrossBow)) {
            boolean pressed = this.isKeyPressed(this.activeBind);
            if (pressed && !this.crossBowPressed) {
               this.crossBowPressed = true;
               this.executeCrossBowMode();
            }
            if (!pressed) {
               this.crossBowPressed = false;
            }
         }
      }
   }

   @Override
   public void onRender2D(class_332 context) {
      if (!fullNullCheck() && this.mode.is(AutoCart.Mode.CrossBow) && !mc.field_1690.field_1842) {
         if (this.hasCrossbowDebugBaseRequirements()) {
            this.renderCrossbowDebug(context);
         }
      }
   }

   private void handleRefill() {
      if (this.reFill.is(AutoCart.ReFillMode.None)) {
         this.clearRefillState();
      } else if (!this.scheduledRefillSwap && mc.field_1755 == null && mc.field_1724.field_6012 == mc.field_1724.field_6235) {
         int targetHotbarSlot = this.refillSlot.getValue() - 1;
         if (mc.field_1724.method_31548().method_5438(targetHotbarSlot).method_7909() != class_1802.field_8069) {
            SearchInvResult cartResult = InventoryUtility.findItemInInventory(class_1802.field_8069);
            if (cartResult.found()) {
               if (this.reFill.is(AutoCart.ReFillMode.Legit)) {
                  this.handleLegitRefill(cartResult.slot(), targetHotbarSlot);
               } else {
                  this.performRefillSwap(cartResult.slot(), targetHotbarSlot);
               }
            }
         }
      }
   }

   private void handleLegitRefill(int inventorySlot, int hotbarSlot) {
      if (MovementUtility.isMoving()) {
         InputBlocker.block(REFILL_BLOCK_OWNER);
         this.scheduleRefillSwap(inventorySlot, hotbarSlot, LEGIT_REFILL_DELAY_MS);
      } else {
         this.performRefillSwap(inventorySlot, hotbarSlot);
      }
   }

   private void scheduleRefillSwap(int inventorySlot, int hotbarSlot, long delayMs) {
      if (inventorySlot == -1) {
         this.clearRefillState();
      } else {
         this.scheduledRefillSwap = true;
         this.scheduledRefillInventorySlot = inventorySlot;
         this.scheduledRefillHotbarSlot = hotbarSlot;
         this.scheduledRefillAt = System.currentTimeMillis() + delayMs;
      }
   }

   private void handleScheduledRefill() {
      if (this.scheduledRefillSwap && System.currentTimeMillis() >= this.scheduledRefillAt) {
         try {
            this.performRefillSwap(this.scheduledRefillInventorySlot, this.scheduledRefillHotbarSlot);
         } finally {
            this.clearRefillState();
         }
      }
   }

   private void performRefillSwap(int inventorySlot, int hotbarSlot) {
      if (inventorySlot != -1 && hotbarSlot >= 0 && hotbarSlot <= 8) {
         if (mc.field_1755 == null && mc.field_1724.field_6012 == mc.field_1724.field_6235) {
            if (mc.field_1724.method_31548().method_5438(hotbarSlot).method_7909() != class_1802.field_8069) {
               InteractionUtility.clickSlot(inventorySlot, hotbarSlot, class_1713.field_7791);
            }
         }
      }
   }

   private void clearRefillState() {
      this.scheduledRefillSwap = false;
      this.scheduledRefillInventorySlot = -1;
      this.scheduledRefillHotbarSlot = -1;
      this.scheduledRefillAt = -1L;
      InputBlocker.unblock(REFILL_BLOCK_OWNER);
   }

   @EventHandler
   public void onTotemPop(@NotNull TotemPopEvent event) {
      if (!fullNullCheck() && !this.cartAuraExecuting) {
         class_1657 popTarget = event.getEntity();
         if (popTarget != mc.field_1724) {
            boolean isAuraTarget = this.killTarget.getValue()
               && ModuleManager.aura.target != null
               && popTarget == ModuleManager.aura.target;
            boolean isOtherPlayer = this.otherPlayers.getValue()
               && !thunder.hack.core.Managers.FRIEND.isFriend(popTarget)
               && mc.field_1724.method_19538().method_1022(popTarget.method_19538()) <= 6.0;

            if (this.mode.is(AutoCart.Mode.CrossBow) && this.cartAura.getValue()) {
               if (isAuraTarget || isOtherPlayer) {
                  class_2338 placePos = this.findCartAuraPosition(popTarget);
                  if (placePos != null) {
                     SearchInvResult crossbowResult = this.findLoadedCrossbowInHotBar();
                     SearchInvResult cartResult = InventoryUtility.findItemInHotBar(class_1802.field_8069);
                     if (crossbowResult.found() && cartResult.found()) {
                        boolean hasFlame = this.hasFlameEnchant(mc.field_1724.method_31548().method_5438(crossbowResult.slot()));
                        SearchInvResult flintResult = InventoryUtility.findItemInHotBar(class_1802.field_8884);
                        if (hasFlame || flintResult.found()) {
                           boolean railExists = this.isRailBlock(mc.field_1687.method_8320(placePos.method_10084()).method_26204());
                           SearchInvResult railResult = this.findRailInHotBar();
                           if (railExists || railResult.found()) {
                              this.executeCartAura(placePos, crossbowResult, railResult, cartResult, flintResult, hasFlame, railExists);
                           }
                        }
                     }
                  }
               }
            }

            if (this.mode.is(AutoCart.Mode.Bow) && this.bowAura.getValue()) {
               if (isAuraTarget || isOtherPlayer) {
                  this.executeBowAura(popTarget);
               }
            }
         }
      }
   }

   private void executeBowAura(@NotNull class_1297 target) {
      if (!fullNullCheck() && this.mode.is(AutoCart.Mode.Bow)) {
         SearchInvResult bowResult = InventoryUtility.findItemInHotBar(class_1802.field_8102);
         SearchInvResult cartResult = InventoryUtility.findItemInHotBar(class_1802.field_8069);
         if (!bowResult.found() || !cartResult.found()) return;
         class_2338 placePos = this.findCartAuraPosition(target);
         if (placePos == null) return;
         class_2338 basePos = this.getCartBasePos(placePos);
         boolean railExists = this.isRailBlock(mc.field_1687.method_8320(basePos.method_10084()).method_26204());
         SearchInvResult railResult = this.findRailInHotBar();
         if (!railExists && !railResult.found()) return;
         float distSq = PlayerUtility.squaredDistanceFromEyes(basePos.method_10084().method_46558());
         float maxDistSq = this.maxDistance.getValue() * this.maxDistance.getValue();
         float safeDistSq = 4.0F;
         if (distSq > maxDistSq || distSq < safeDistSq) return;
         this.cartAuraExecuting = true;
         int prevSlot = mc.field_1724.method_31548().field_7545;
         int delayMs = this.delay.getValue();
         int startDelayMs = this.cartAuraDelay.getValue() * 50;
         SearchInvResult finalRailResult = railResult;
         boolean finalRailExists = railExists;
         Managers.ASYNC.run(() -> {
            try {
               if (startDelayMs > 0) {
                  AsyncManager.sleep(startDelayMs);
               }
               if (!fullNullCheck() && !this.isDisabled() && this.mode.is(AutoCart.Mode.Bow) && this.bowAura.getValue()) {
                  ModuleManager.aura.externalPause = true;
                  class_243 placeVec = new class_243(basePos.method_10263() + 0.5, basePos.method_10084().method_10264(), basePos.method_10260() + 0.5);
                  mc.execute(() -> this.applyRotation(InteractionUtility.calculateAngle(placeVec)));
                  AsyncManager.sleep(delayMs);
                  if (!finalRailExists) {
                     mc.execute(() -> {
                        if (!this.isRailBlock(mc.field_1687.method_8320(basePos.method_10084()).method_26204())) {
                           this.selectHotbarLegit(finalRailResult.slot());
                           this.placeRailOn(basePos);
                        }
                     });
                     AsyncManager.sleep(delayMs);
                  }
                  mc.execute(() -> {
                     this.selectHotbarLegit(cartResult.slot());
                     this.placeMinecartOn(basePos);
                  });
                  AsyncManager.sleep(delayMs);
                  mc.execute(() -> {
                     if (this.swapBack.getValue()) {
                        this.selectHotbarLegit(prevSlot);
                     }
                     ModuleManager.aura.externalPause = false;
                     this.cartAuraExecuting = false;
                     this.endRotation();
                  });
               } else {
                  ModuleManager.aura.externalPause = false;
                  this.cartAuraExecuting = false;
               }
            } catch (Exception e) {
               e.printStackTrace();
               ModuleManager.aura.externalPause = false;
               this.cartAuraExecuting = false;
            }
         });
      }
   }

   private void executeCartAura(
      class_2338 basePos,
      SearchInvResult crossbowResult,
      SearchInvResult railResult,
      SearchInvResult cartResult,
      SearchInvResult flintResult,
      boolean hasFlame,
      boolean railExists
   ) {
      this.cartAuraExecuting = true;
      int prevSlot = mc.field_1724.method_31548().field_7545;
      int delayMs = this.delay.getValue();
      int startDelayMs = this.cartAuraDelay.getValue() * 50;
      Managers.ASYNC.run(() -> {
         try {
            if (startDelayMs > 0) {
               AsyncManager.sleep(startDelayMs);
            }
            if (!fullNullCheck() && !this.isDisabled() && this.mode.is(AutoCart.Mode.CrossBow) && this.cartAura.getValue()) {
               ModuleManager.aura.externalPause = true;
               class_243 placeVec = new class_243(basePos.method_10263() + 0.5, basePos.method_10084().method_10264(), basePos.method_10260() + 0.5);
               mc.execute(() -> this.applyRotation(InteractionUtility.calculateAngle(placeVec)));
               AsyncManager.sleep(delayMs);
               if (!railExists) {
                  mc.execute(() -> {
                     if (!this.isRailBlock(mc.field_1687.method_8320(basePos.method_10084()).method_26204())) {
                        this.selectHotbarLegit(railResult.slot());
                        this.placeRailOn(basePos);
                     }
                  });
                  AsyncManager.sleep(delayMs);
               }
               mc.execute(() -> {
                  this.selectHotbarLegit(cartResult.slot());
                  this.placeMinecartOn(basePos);
               });
               AsyncManager.sleep(delayMs);
               if (!hasFlame) {
                  class_2338 firePos = this.findFirePosition(basePos);
                  if (firePos != null) {
                     mc.execute(() -> {
                        class_243 fireVec = new class_243(firePos.method_10263() + 0.5, firePos.method_10264() + 1.0, firePos.method_10260() + 0.5);
                        this.applyRotation(InteractionUtility.calculateAngle(fireVec));
                        this.selectHotbarLegit(flintResult.slot());
                        class_3965 fireHit = new class_3965(fireVec, class_2350.field_11036, firePos, false);
                        this.interactWithBlock(fireHit);
                     });
                     AsyncManager.sleep(delayMs);
                  }
               }
               mc.execute(() -> {
                  class_243 minecartCenter = new class_243(basePos.method_10263() + 0.5, basePos.method_10264() + 1.5, basePos.method_10260() + 0.5);
                  float[] shootAngle = InteractionUtility.calculateAngle(minecartCenter);
                  this.applyRotation(shootAngle);
                  this.selectHotbarLegit(crossbowResult.slot());
                  this.interactWithItem();
                  mc.field_1724.method_6104(class_1268.field_5808);
                  ModuleManager.aura.externalPause = false;
                  this.cartAuraExecuting = false;
               });
               AsyncManager.sleep(delayMs);
               mc.execute(() -> {
                  if (this.swapBack.getValue()) {
                     this.selectHotbarLegit(prevSlot);
                  }
                  this.endRotation();
               });
            } else {
               ModuleManager.aura.externalPause = false;
               this.cartAuraExecuting = false;
            }
         } catch (Exception e) {
            e.printStackTrace();
            ModuleManager.aura.externalPause = false;
            this.cartAuraExecuting = false;
         }
      });
   }

   private void executeBowMode() {
      if (!fullNullCheck()) {
         SearchInvResult bowResult = InventoryUtility.findItemInHotBar(class_1802.field_8102);
         SearchInvResult cartResult = InventoryUtility.findItemInHotBar(class_1802.field_8069);
         if (bowResult.found() && cartResult.found()) {
            class_2338 targetPos = this.calcBowTrajectory(mc.field_1724.method_36454());
            if (targetPos != null) {
               class_2338 basePos = this.getCartBasePos(targetPos);
               boolean railExists = this.isRailBlock(mc.field_1687.method_8320(basePos.method_10084()).method_26204());
               if (railExists || this.findRailInHotBar().found()) {
                  float distSq = PlayerUtility.squaredDistanceFromEyes(basePos.method_10084().method_46558());
                  float maxDistSq = this.maxDistance.getValue() * this.maxDistance.getValue();
                  float safeDistSq = 4.0F;
                  if (!(distSq > maxDistSq) && !(distSq < safeDistSq)) {
                     Managers.ASYNC.run(() -> this.executeBowPlacement(targetPos), this.startDelay.getValue().intValue());
                  }
               }
            }
         }
      }
   }

   private void executeBowPlacement(@NotNull class_2338 targetPos) {
      if (!fullNullCheck() && this.mode.is(AutoCart.Mode.Bow)) {
         class_2338 basePos = this.getCartBasePos(targetPos);
         boolean railExists = this.isRailBlock(mc.field_1687.method_8320(basePos.method_10084()).method_26204());
         SearchInvResult railResult = this.findRailInHotBar();
         SearchInvResult cartResult = InventoryUtility.findItemInHotBar(class_1802.field_8069);
         if (cartResult.found()) {
            if (railExists || railResult.found()) {
               int prevSlot = mc.field_1724.method_31548().field_7545;
               int delayMs = this.delay.getValue();
               class_243 placeVec = new class_243(basePos.method_10263() + 0.5, basePos.method_10084().method_10264(), basePos.method_10260() + 0.5);
               mc.execute(() -> this.applyRotation(InteractionUtility.calculateAngle(placeVec)));
               AsyncManager.sleep(delayMs);
               if (!railExists) {
                  mc.execute(() -> {
                     if (!this.isRailBlock(mc.field_1687.method_8320(basePos.method_10084()).method_26204())) {
                        this.selectHotbarLegit(railResult.slot());
                        this.placeRailOn(basePos);
                     }
                  });
                  AsyncManager.sleep(delayMs);
               }
               mc.execute(() -> {
                  this.selectHotbarLegit(cartResult.slot());
                  this.placeMinecartOn(basePos);
               });
               AsyncManager.sleep(delayMs);
               mc.execute(() -> {
                  if (this.swapBack.getValue()) {
                     this.selectHotbarLegit(prevSlot);
                  }
                  this.endRotation();
               });
            }
         }
      }
   }

   private void executeCrossBowMode() {
      if (!fullNullCheck()) {
         SearchInvResult crossbowResult = this.findLoadedCrossbowInHotBar();
         SearchInvResult railResult = this.findRailInHotBar();
         SearchInvResult cartResult = InventoryUtility.findItemInHotBar(class_1802.field_8069);
         if (crossbowResult.found() && railResult.found() && cartResult.found()) {
            boolean hasFlame = this.hasFlameEnchant(mc.field_1724.method_31548().method_5438(crossbowResult.slot()));
            SearchInvResult flintResult = InventoryUtility.findItemInHotBar(class_1802.field_8884);
            if (hasFlame || flintResult.found()) {
               class_3965 rayResult = this.rayTraceFromEyes(4.5);
               if (rayResult != null && rayResult.method_17783() == class_240.field_1332) {
                  class_2338 hitPos = rayResult.method_17777();
                  if (!(PlayerUtility.squaredDistanceFromEyes(hitPos.method_46558()) > 20.25F)) {
                     class_2338 basePos;
                     if (mc.field_1687.method_8320(hitPos).method_26215()) {
                        basePos = hitPos.method_10074();
                     } else {
                        basePos = hitPos;
                     }
                     class_2338 fireBlockPos = null;
                     if (!hasFlame) {
                        fireBlockPos = this.findFirePosition(basePos);
                        if (fireBlockPos == null) {
                           return;
                        }
                     }
                     class_2338 finalFireBlockPos = fireBlockPos;
                     int prevSlot = mc.field_1724.method_31548().field_7545;
                     int delayMs = this.delay.getValue();
                     Managers.ASYNC.run(() -> {
                        class_243 placeVec = new class_243(basePos.method_10263() + 0.5, basePos.method_10084().method_10264(), basePos.method_10260() + 0.5);
                        mc.execute(() -> this.applyRotation(InteractionUtility.calculateAngle(placeVec)));
                        AsyncManager.sleep(delayMs);
                        mc.execute(() -> {
                           if (!this.isRailBlock(mc.field_1687.method_8320(basePos.method_10084()).method_26204())) {
                              this.selectHotbarLegit(railResult.slot());
                              this.placeRailOn(basePos);
                           }
                        });
                        AsyncManager.sleep(delayMs);
                        mc.execute(() -> {
                           this.selectHotbarLegit(cartResult.slot());
                           this.placeMinecartOn(basePos);
                        });
                        AsyncManager.sleep(delayMs);
                        if (!hasFlame && finalFireBlockPos != null) {
                           mc.execute(() -> {
                              class_243 fireVec = new class_243(
                                 finalFireBlockPos.method_10263() + 0.5,
                                 finalFireBlockPos.method_10264() + 1.0,
                                 finalFireBlockPos.method_10260() + 0.5
                              );
                              this.applyRotation(InteractionUtility.calculateAngle(fireVec));
                              this.selectHotbarLegit(flintResult.slot());
                              class_3965 fireHit = new class_3965(fireVec, class_2350.field_11036, finalFireBlockPos, false);
                              this.interactWithBlock(fireHit);
                           });
                           AsyncManager.sleep(delayMs);
                        }
                        mc.execute(() -> {
                           class_243 minecartCenter = new class_243(basePos.method_10263() + 0.5, basePos.method_10264() + 1.5, basePos.method_10260() + 0.5);
                           float[] shootAngle = InteractionUtility.calculateAngle(minecartCenter);
                           this.applyRotation(shootAngle);
                           this.selectHotbarLegit(crossbowResult.slot());
                           this.interactWithItem();
                           mc.field_1724.method_6104(class_1268.field_5808);
                        });
                        AsyncManager.sleep(delayMs);
                        mc.execute(() -> {
                           if (this.swapBack.getValue()) {
                              this.selectHotbarLegit(prevSlot);
                           }
                           this.endRotation();
                        });
                     });
                  }
               }
            }
         }
      }
   }

   private void applyRotation(float[] angle) {
      if (this.changeLook.getValue()) {
         mc.field_1724.method_36456(angle[0]);
         mc.field_1724.method_36457(angle[1]);
      } else {
         this.silentRotation = angle;
         this.rotating = true;
      }
   }

   private void endRotation() {
      this.silentRotation = null;
      this.rotating = false;
   }

   public boolean isAutoCartMoveFixActive() {
      return this.getAutoCartMoveFixRotation() != null;
   }

   @Nullable
   private float[] getAutoCartMoveFixRotation() {
      float[] rotation = this.silentRotation;
      return this.isOn() && !fullNullCheck() && !this.changeLook.getValue() && this.rotating && rotation != null && !mc.field_1724.method_3144() ? rotation : null;
   }

   private class_243 fixMovement(float yaw, class_243 movementInput, float speed) {
      double lengthSquared = movementInput.method_1027();
      if (lengthSquared < 1.0E-7) {
         return class_243.field_1353;
      }
      class_243 movement = (lengthSquared > 1.0 ? movementInput.method_1029() : movementInput).method_1021(speed);
      float sin = class_3532.method_15374(yaw * (float) (Math.PI / 180.0));
      float cos = class_3532.method_15362(yaw * (float) (Math.PI / 180.0));
      return new class_243(movement.field_1352 * cos - movement.field_1350 * sin, movement.field_1351, movement.field_1350 * cos + movement.field_1352 * sin);
   }

   private void selectHotbarLegit(int slot) {
      if (mc.field_1724 != null) {
         mc.field_1724.method_31548().field_7545 = slot;
      }
   }

   private void runWithInteractionRotation(@Nullable float[] angle, Runnable action) {
      if (mc.field_1724 != null) {
         if (!this.changeLook.getValue() && angle != null) {
            float prevYaw = mc.field_1724.method_36454();
            float prevPitch = mc.field_1724.method_36455();
            mc.field_1724.method_36456(angle[0]);
            mc.field_1724.method_36457(angle[1]);
            try {
               action.run();
            } finally {
               mc.field_1724.method_36456(prevYaw);
               mc.field_1724.method_36457(prevPitch);
            }
         } else {
            action.run();
         }
      }
   }

   private void interactWithItem() {
      if (mc.field_1724 != null && mc.field_1761 != null) {
         this.runWithInteractionRotation(this.silentRotation, () -> mc.field_1761.method_2919(mc.field_1724, class_1268.field_5808));
      }
   }

   private void placeRailOn(class_2338 basePos) {
      if (mc.field_1687 != null && mc.field_1724 != null && mc.field_1761 != null) {
         class_3965 hitResult = new class_3965(
            new class_243(basePos.method_10263() + 0.5, basePos.method_10084().method_10264(), basePos.method_10260() + 0.5), class_2350.field_11036, basePos, false
         );
         this.interactWithBlock(hitResult);
      }
   }

   private void placeMinecartOn(class_2338 basePos) {
      if (mc.field_1687 != null && mc.field_1724 != null && mc.field_1761 != null) {
         class_3965 hitResult = new class_3965(
            new class_243(basePos.method_10263() + 0.5, basePos.method_10084().method_10264() + 0.125, basePos.method_10260() + 0.5), class_2350.field_11036, basePos.method_10084(), false
         );
         this.interactWithBlock(hitResult);
      }
   }

   private void interactWithBlock(class_3965 hitResult) {
      this.runWithInteractionRotation(this.silentRotation, () -> mc.field_1761.method_2896(mc.field_1724, class_1268.field_5808, hitResult));
      mc.field_1724.method_6104(class_1268.field_5808);
   }

   @NotNull
   private class_2338 getCartBasePos(@NotNull class_2338 targetPos) {
      class_2680 targetState = mc.field_1687.method_8320(targetPos);
      return !this.isRailBlock(targetState.method_26204()) && !targetState.method_26215() ? targetPos : targetPos.method_10074();
   }

   @Nullable
   private class_2338 calcBowTrajectory(float yaw) {
      if (mc.field_1724 != null && mc.field_1687 != null) {
         double x = Render2DEngine.interpolate(mc.field_1724.field_6014, mc.field_1724.method_23317(), Render3DEngine.getTickDelta());
         double y = Render2DEngine.interpolate(mc.field_1724.field_6036, mc.field_1724.method_23318(), Render3DEngine.getTickDelta());
         double z = Render2DEngine.interpolate(mc.field_1724.field_5969, mc.field_1724.method_23321(), Render3DEngine.getTickDelta());
         y += mc.field_1724.method_18381(mc.field_1724.method_18376()) - 0.1000000014901161;
         float pitch = mc.field_1724.method_36455();
         double motionX = -class_3532.method_15374(yaw / 180.0F * (float) Math.PI) * class_3532.method_15362(pitch / 180.0F * (float) Math.PI);
         double motionY = -class_3532.method_15374(pitch / 180.0F * (float) Math.PI);
         double motionZ = class_3532.method_15362(yaw / 180.0F * (float) Math.PI) * class_3532.method_15362(pitch / 180.0F * (float) Math.PI);
         float power = mc.field_1724.method_6048() / 20.0F;
         power = (power * power + power * 2.0F) / 3.0F;
         if (power > 1.0F) {
            power = 1.0F;
         }
         if (power < 0.1F) {
            return null;
         }
         float dist = class_3532.method_15355((float)(motionX * motionX + motionY * motionY + motionZ * motionZ));
         motionX /= dist;
         motionY /= dist;
         motionZ /= dist;
         float pow = power * 3.0F;
         motionX *= pow;
         motionY *= pow;
         motionZ *= pow;
         if (!mc.field_1724.method_24828()) {
            motionY += mc.field_1724.method_5861(mc.field_1687.method_8597().comp_655().equals("minecraft:the_nether") ? null : null);
         }
         for (int i = 0; i < 300; i++) {
            class_243 lastPos = new class_243(x, y, z);
            x += motionX;
            y += motionY;
            z += motionZ;
            motionX *= 0.99;
            motionY *= 0.99;
            motionZ *= 0.99;
            motionY -= 0.05F;
            for (class_1297 ent : mc.field_1687.method_18112()) {
               if (!(ent instanceof class_1667)
                  && !ent.equals(mc.field_1724)
                  && ent.method_5829().method_994(new class_238(x - 0.3, y - 0.3, z - 0.3, x + 0.3, y + 0.3, z + 0.3))) {
                  return null;
               }
            }
            class_243 pos = new class_243(x, y, z);
            class_3965 bhr = mc.field_1687.method_17742(new class_3959(lastPos, pos, class_3960.field_17559, class_242.field_1348, mc.field_1724));
            if (bhr != null && bhr.method_17783() == class_240.field_1332) {
               return bhr.method_17777();
            }
            if (y <= -65.0) {
               break;
            }
         }
         return null;
      } else {
         return null;
      }
   }

   @Nullable
   private class_2338 findCartAuraPosition(class_1297 target) {
      if (mc.field_1724 != null && mc.field_1687 != null) {
         class_243 playerEyes = InteractionUtility.getEyesPos(mc.field_1724);
         class_238 targetBox = target.method_5829();
         class_243 targetFeet = new class_243(target.method_23317(), targetBox.field_1322, target.method_23321());
         class_2338 targetBlockPos = target.method_24515();
         int r = 4;
         class_2338 bestPos = null;
         double bestDist = Double.MAX_VALUE;
         for (int x = targetBlockPos.method_10263() - r; x <= targetBlockPos.method_10263() + r; x++) {
            for (int z = targetBlockPos.method_10260() - r; z <= targetBlockPos.method_10260() + r; z++) {
               for (int y = targetBlockPos.method_10264() - r; y <= targetBlockPos.method_10264(); y++) {
                  class_2338 bp = new class_2338(x, y, z);
                  if (mc.field_1687.method_8320(bp).method_51367() && !(bp.method_10264() + 1 > targetBox.field_1322)) {
                     class_2680 aboveState = mc.field_1687.method_8320(bp.method_10084());
                     if (aboveState.method_26215() || this.isRailBlock(aboveState.method_26204())) {
                        class_243 surfacePos = new class_243(bp.method_10263() + 0.5, bp.method_10264() + 1.0, bp.method_10260() + 0.5);
                        if (!(PlayerUtility.squaredDistanceFromEyes(surfacePos) > 20.25F)) {
                           class_3965 blockCheck = mc.field_1687.method_17742(
                              new class_3959(playerEyes, surfacePos, class_3960.field_17558, class_242.field_1348, mc.field_1724)
                           );
                           if ((blockCheck == null || blockCheck.method_17783() != class_240.field_1332 || blockCheck.method_17777().equals(bp))
                              && this.isPathClearOfPlayers(playerEyes, surfacePos, target)) {
                              class_243 cartCenter = new class_243(bp.method_10263() + 0.5, bp.method_10264() + 1.5, bp.method_10260() + 0.5);
                              class_3965 damageCheck = mc.field_1687.method_17742(
                                 new class_3959(cartCenter, targetFeet, class_3960.field_17558, class_242.field_1348, mc.field_1724)
                              );
                              if (damageCheck == null || damageCheck.method_17783() != class_240.field_1332) {
                                 double distToTarget = cartCenter.method_1025(targetFeet);
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
      } else {
         return null;
      }
   }

   private boolean isPathClearOfPlayers(class_243 from, class_243 to, class_1297 exclude) {
      if (mc.field_1687 == null) {
         return true;
      }
      for (class_1657 player : mc.field_1687.method_18456()) {
         if (player != mc.field_1724 && player != exclude && player.method_5829().method_992(from, to).isPresent()) {
            return false;
         }
      }
      return true;
   }

   private SearchInvResult findRailInHotBar() {
      return InventoryUtility.findItemInHotBar(class_1802.field_8129, class_1802.field_8655, class_1802.field_8211, class_1802.field_8848);
   }

   private void renderCrossbowDebug(class_332 context) {
      int hotbarLeft = mc.method_22683().method_4486() / 2 - 91;
      int hotbarTop = mc.method_22683().method_4502() - 22;
      for (int slot = 0; slot < 9; slot++) {
         class_1799 stack = mc.field_1724.method_31548().method_5438(slot);
         if (this.isCrossbowDebugCandidate(stack)) {
            float slotX = hotbarLeft + slot * 20 + 1;
            float slotY = hotbarTop + 1;
            Render2DEngine.drawRect(context.method_51448(), slotX, slotY, 20.0F, 20.0F, CROSSBOW_DEBUG_FILL);
         }
      }
   }

   private boolean hasCrossbowDebugBaseRequirements() {
      if (this.findRailInHotBar().found() && InventoryUtility.findItemInHotBar(class_1802.field_8069).found()) {
         if (!this.hasArrowAmmoInInventory()) {
            return false;
         }
         for (int slot = 0; slot < 9; slot++) {
            if (this.isCrossbowDebugCandidate(mc.field_1724.method_31548().method_5438(slot))) {
               return true;
            }
         }
         return false;
      } else {
         return false;
      }
   }

   private boolean isCrossbowDebugCandidate(class_1799 stack) {
      return !this.isUnloadedCrossbow(stack) ? false : this.hasFlameEnchant(stack) || InventoryUtility.findItemInHotBar(class_1802.field_8884).found();
   }

   private boolean hasArrowAmmoInInventory() {
      for (class_1799 stack : mc.field_1724.method_31548().field_7547) {
         if (this.isArrowStack(stack)) {
            return true;
         }
      }
      for (class_1799 stack : mc.field_1724.method_31548().field_7544) {
         if (this.isArrowStack(stack)) {
            return true;
         }
      }
      return false;
   }

   private boolean isArrowStack(class_1799 stack) {
      return !stack.method_7960() && stack.method_7909() instanceof class_1744;
   }

   private boolean isUnloadedCrossbow(class_1799 stack) {
      return stack.method_7909() == class_1802.field_8399 && !this.isCrossbowCharged(stack);
   }

   private boolean isCrossbowCharged(class_1799 stack) {
      return stack.method_7909() == class_1802.field_8399
         && stack.method_57824(class_9334.field_49649) != null
         && !stack.method_57824(class_9334.field_49649).method_57442();
   }

   private SearchInvResult findLoadedCrossbowInHotBar() {
      return InventoryUtility.findInHotBar(this::isCrossbowCharged);
   }

   private boolean isRailBlock(class_2248 block) {
      return block == class_2246.field_10167 || block == class_2246.field_10425 || block == class_2246.field_10025 || block == class_2246.field_10546;
   }

   private boolean hasFlameEnchant(class_1799 stack) {
      if (mc.field_1687 == null) return false;
      class_6880<net.minecraft.class_1887> flame = mc.field_1687.method_30349()
         .method_30530(class_1893.field_9126.method_58273())
         .method_40264(class_1893.field_9126)
         .get();
      return class_1890.method_8225(flame, stack) > 0;
   }

   @Nullable
   private class_3965 rayTraceFromEyes(double maxRange) {
      if (mc.field_1724 != null && mc.field_1687 != null) {
         class_243 eyePos = mc.field_1724.method_33571();
         class_243 lookVec = mc.field_1724.method_5828(1.0F);
         class_243 endPos = eyePos.method_1019(lookVec.method_1021(maxRange));
         return mc.field_1687.method_17742(new class_3959(eyePos, endPos, class_3960.field_17559, class_242.field_1348, mc.field_1724));
      } else {
         return null;
      }
   }

   @Nullable
   private class_2338 findFirePosition(class_2338 targetPos) {
      if (mc.field_1724 != null && mc.field_1687 != null) {
         class_243 eyePos = mc.field_1724.method_33571();
         class_243 minecartPos = new class_243(targetPos.method_10263() + 0.5, targetPos.method_10264() + 1.5, targetPos.method_10260() + 0.5);
         class_243 dir = minecartPos.method_1020(eyePos).method_1029();
         class_2350[] horizontals = new class_2350[]{class_2350.field_11043, class_2350.field_11035, class_2350.field_11034, class_2350.field_11039};
         class_2338 bestPos = null;
         double bestScore = Double.MAX_VALUE;
         for (class_2350 d : horizontals) {
            class_2338 candidate = targetPos.method_10093(d);
            if (mc.field_1687.method_8320(candidate).method_51367() && mc.field_1687.method_22347(candidate.method_10084())) {
               class_243 fireCenter = new class_243(candidate.method_10263() + 0.5, candidate.method_10264() + 1.0, candidate.method_10260() + 0.5);
               class_243 eyeToFire = fireCenter.method_1020(eyePos);
               double t = eyeToFire.method_1026(dir);
               if (!(t <= 0.0)) {
                  double eyeToMinecart = minecartPos.method_1020(eyePos).method_1026(dir);
                  if (!(t >= eyeToMinecart)) {
                     class_243 closest = eyePos.method_1019(dir.method_1021(t));
                     double distToLine = closest.method_1022(fireCenter);
                     if (distToLine < bestScore) {
                        bestScore = distToLine;
                        bestPos = candidate;
                     }
                  }
               }
            }
         }
         if (bestPos == null) {
            for (class_2350 d : horizontals) {
               class_2338 adj = targetPos.method_10093(d);
               if (mc.field_1687.method_8320(adj).method_51367() && mc.field_1687.method_22347(adj.method_10084())) {
                  return adj;
               }
            }
         }
         return bestPos;
      } else {
         return null;
      }
   }

   @Override
   public String getDisplayInfo() {
      return this.mode.getValue().name();
   }

   public enum Mode {
      Bow,
      CrossBow;
   }

   public enum ReFillMode {
      None,
      Normal,
      Legit;
   }
}
