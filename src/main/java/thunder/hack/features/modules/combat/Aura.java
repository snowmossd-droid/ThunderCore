package thunder.hack.features.modules.combat;

import baritone.api.BaritoneAPI;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.class_1268;
import net.minecraft.class_1294;
import net.minecraft.class_1297;
import net.minecraft.class_1308;
import net.minecraft.class_1309;
import net.minecraft.class_1429;
import net.minecraft.class_1451;
import net.minecraft.class_1531;
import net.minecraft.class_1588;
import net.minecraft.class_1621;
import net.minecraft.class_1646;
import net.minecraft.class_1657;
import net.minecraft.class_1674;
import net.minecraft.class_1676;
import net.minecraft.class_1678;
import net.minecraft.class_1713;
import net.minecraft.class_1743;
import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_1829;
import net.minecraft.class_1835;
import net.minecraft.class_2246;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_238;
import net.minecraft.class_243;
import net.minecraft.class_2663;
import net.minecraft.class_2708;
import net.minecraft.class_2815;
import net.minecraft.class_2824;
import net.minecraft.class_2828;
import net.minecraft.class_2846;
import net.minecraft.class_2848;
import net.minecraft.class_2868;
import net.minecraft.class_2886;
import net.minecraft.class_3532;
import net.minecraft.class_4587;
import net.minecraft.class_5134;
import net.minecraft.class_745;
import net.minecraft.item.*;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.util.math.*;
import org.jetbrains.annotations.NotNull;
import thunder.hack.ThunderHack;
import thunder.hack.core.Core;
import thunder.hack.core.Managers;
import thunder.hack.core.manager.client.ModuleManager;
import thunder.hack.events.impl.EventSync;
import thunder.hack.events.impl.PacketEvent;
import thunder.hack.events.impl.PlayerUpdateEvent;
import thunder.hack.gui.notification.Notification;
import thunder.hack.injection.accesors.ILivingEntity;
import thunder.hack.features.modules.Module;
import thunder.hack.features.modules.client.HudEditor;
import thunder.hack.setting.Setting;
import thunder.hack.setting.impl.BooleanSettingGroup;
import thunder.hack.setting.impl.SettingGroup;
import thunder.hack.utility.Timer;
import thunder.hack.utility.interfaces.IOtherClientPlayerEntity;
import thunder.hack.utility.math.MathUtility;
import thunder.hack.utility.player.InteractionUtility;
import thunder.hack.utility.player.InventoryUtility;
import thunder.hack.utility.player.PlayerUtility;
import thunder.hack.utility.player.SearchInvResult;
import thunder.hack.utility.render.Render2DEngine;
import thunder.hack.utility.render.Render3DEngine;
import thunder.hack.utility.render.animation.CaptureMark;

import java.awt.*;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

import static net.minecraft.class_1839.field_8949;
import static net.minecraft.class_3532.method_15338;
import static thunder.hack.features.modules.client.ClientSettings.isRu;
import static thunder.hack.utility.math.MathUtility.random;

public class Aura extends Module {
    public final Setting<Float> attackRange = new Setting<>("Range", 3.1f, 1f, 6.0f);
    public final Setting<Float> wallRange = new Setting<>("ThroughWallsRange", 3.1f, 0f, 6.0f);
    public final Setting<Boolean> elytra = new Setting<>("ElytraOverride",false);
    public final Setting<Float> elytraAttackRange = new Setting<>("ElytraRange", 3.1f, 1f, 6.0f, v -> elytra.getValue());
    public final Setting<Float> elytraWallRange = new Setting<>("ElytraThroughWallsRange", 3.1f, 0f, 6.0f,v -> elytra.getValue());
    public final Setting<WallsBypass> wallsBypass = new Setting<>("WallsBypass", WallsBypass.Off, v -> getWallRange() > 0);
    public final Setting<Integer> fov = new Setting<>("FOV", 180, 1, 180);
    public final Setting<Mode> rotationMode = new Setting<>("RotationMode", Mode.Track);
    public final Setting<Integer> interactTicks = new Setting<>("InteractTicks", 3, 1, 10, v -> rotationMode.getValue() == Mode.Interact);
    public final Setting<Switch> switchMode = new Setting<>("AutoWeapon", Switch.None);
    public final Setting<Boolean> onlyWeapon = new Setting<>("OnlyWeapon", false, v -> switchMode.getValue() != Switch.Silent);
    public final Setting<BooleanSettingGroup> smartCrit = new Setting<>("SmartCrit", new BooleanSettingGroup(true));
    public final Setting<Boolean> onlySpace = new Setting<>("OnlyCrit", false).addToGroup(smartCrit);
    public final Setting<Boolean> autoJump = new Setting<>("AutoJump", false).addToGroup(smartCrit);
    public final Setting<Boolean> shieldBreaker = new Setting<>("ShieldBreaker", true);
    public final Setting<Boolean> pauseWhileEating = new Setting<>("PauseWhileEating", false);
    public final Setting<Boolean> tpsSync = new Setting<>("TPSSync", false);
    public final Setting<Boolean> clientLook = new Setting<>("ClientLook", false);
    public final Setting<Boolean> pauseBaritone = new Setting<>("PauseBaritone", false);
    public final Setting<BooleanSettingGroup> oldDelay = new Setting<>("OldDelay", new BooleanSettingGroup(false));
    public final Setting<Integer> minCPS = new Setting<>("MinCPS", 7, 1, 20).addToGroup(oldDelay);
    public final Setting<Integer> maxCPS = new Setting<>("MaxCPS", 12, 1, 20).addToGroup(oldDelay);

    public final Setting<ESP> esp = new Setting<>("ESP", ESP.ThunderHack);
    public final Setting<SettingGroup> espGroup = new Setting<>("ESPSettings", new SettingGroup(false, 0), v -> esp.is(ESP.ThunderHackV2));
    public final Setting<Integer> espLength = new Setting<>("ESPLength", 14, 1, 40, v -> esp.is(ESP.ThunderHackV2)).addToGroup(espGroup);
    public final Setting<Integer> espFactor = new Setting<>("ESPFactor", 8, 1, 20, v -> esp.is(ESP.ThunderHackV2)).addToGroup(espGroup);
    public final Setting<Float> espShaking = new Setting<>("ESPShaking", 1.8f, 1.5f, 10f, v -> esp.is(ESP.ThunderHackV2)).addToGroup(espGroup);
    public final Setting<Float> espAmplitude = new Setting<>("ESPAmplitude", 3f, 0.1f, 8f, v -> esp.is(ESP.ThunderHackV2)).addToGroup(espGroup);

    public final Setting<SettingGroup> orbitGroup = new Setting<>("OrbitSettings", new SettingGroup(false, 0), v -> esp.is(ESP.Orbit));
    public final Setting<Float> orbitRadius = new Setting<>("OrbitRadius", 1.0f, 0.3f, 3.0f, v -> esp.is(ESP.Orbit)).addToGroup(orbitGroup);
    public final Setting<Float> orbitSpeed = new Setting<>("OrbitSpeed", 2.0f, 0.5f, 10.0f, v -> esp.is(ESP.Orbit)).addToGroup(orbitGroup);
    public final Setting<Integer> orbitCount = new Setting<>("OrbitCount", 3, 1, 8, v -> esp.is(ESP.Orbit)).addToGroup(orbitGroup);
    public final Setting<Float> orbitSize = new Setting<>("OrbitSize", 0.15f, 0.05f, 0.5f, v -> esp.is(ESP.Orbit)).addToGroup(orbitGroup);

    public final Setting<Sort> sort = new Setting<>("Sort", Sort.LowestDistance);
    public final Setting<Boolean> lockTarget = new Setting<>("LockTarget", true);
    public final Setting<Boolean> elytraTarget = new Setting<>("ElytraTarget", true);

    /*   ADVANCED   */
    public final Setting<SettingGroup> advanced = new Setting<>("Advanced", new SettingGroup(false, 0));
    public final Setting<Float> aimRange = new Setting<>("AimRange", 3.1f, 0f, 6.0f).addToGroup(advanced);
    public final Setting<Boolean> randomHitDelay = new Setting<>("RandomHitDelay", false).addToGroup(advanced);
    public final Setting<Boolean> pauseInInventory = new Setting<>("PauseInInventory", true).addToGroup(advanced);
    public final Setting<Boolean> dropSprint = new Setting<>("DropSprint", true).addToGroup(advanced);
    public final Setting<Boolean> returnSprint = new Setting<>("ReturnSprint", true, v -> dropSprint.getValue()).addToGroup(advanced);
    public final Setting<RayTrace> rayTrace = new Setting<>("RayTrace", RayTrace.OnlyTarget).addToGroup(advanced);
    public final Setting<Boolean> grimRayTrace = new Setting<>("GrimRayTrace", true).addToGroup(advanced);
    public final Setting<Boolean> unpressShield = new Setting<>("UnpressShield", true).addToGroup(advanced);
    public final Setting<Boolean> deathDisable = new Setting<>("DisableOnDeath", true).addToGroup(advanced);
    public final Setting<Boolean> tpDisable = new Setting<>("TPDisable", false).addToGroup(advanced);
    public final Setting<Boolean> pullDown = new Setting<>("FastFall", false).addToGroup(advanced);
    public final Setting<Boolean> onlyJumpBoost = new Setting<>("OnlyJumpBoost", false, v -> pullDown.getValue()).addToGroup(advanced);
    public final Setting<Float> pullValue = new Setting<>("PullValue", 3f, 0f, 20f, v -> pullDown.getValue()).addToGroup(advanced);
    public final Setting<AttackHand> attackHand = new Setting<>("AttackHand", AttackHand.MainHand).addToGroup(advanced);
    public final Setting<Resolver> resolver = new Setting<>("Resolver", Resolver.Advantage).addToGroup(advanced);
    public final Setting<Integer> backTicks = new Setting<>("BackTicks", 4, 1, 20, v -> resolver.is(Resolver.BackTrack)).addToGroup(advanced);
    public final Setting<Boolean> resolverVisualisation = new Setting<>("ResolverVisualisation", false, v -> !resolver.is(Resolver.Off)).addToGroup(advanced);
    public final Setting<AccelerateOnHit> accelerateOnHit = new Setting<>("AccelerateOnHit", AccelerateOnHit.Off).addToGroup(advanced);
    public final Setting<Integer> minYawStep = new Setting<>("MinYawStep", 65, 1, 180).addToGroup(advanced);
    public final Setting<Integer> maxYawStep = new Setting<>("MaxYawStep", 75, 1, 180).addToGroup(advanced);
    public final Setting<Float> aimedPitchStep = new Setting<>("AimedPitchStep", 1f, 0f, 90f).addToGroup(advanced);
    public final Setting<Float> maxPitchStep = new Setting<>("MaxPitchStep", 8f, 1f, 90f).addToGroup(advanced);
    public final Setting<Float> pitchAccelerate = new Setting<>("PitchAccelerate", 1.65f, 1f, 10f).addToGroup(advanced);
    public final Setting<Float> attackCooldown = new Setting<>("AttackCooldown", 0.9f, 0.5f, 1f).addToGroup(advanced);
    public final Setting<Float> attackBaseTime = new Setting<>("AttackBaseTime", 0.5f, 0f, 2f).addToGroup(advanced);
    public final Setting<Integer> attackTickLimit = new Setting<>("AttackTickLimit", 11, 0, 20).addToGroup(advanced);
    public final Setting<Float> critFallDistance = new Setting<>("CritFallDistance", 0f, 0f, 1f).addToGroup(advanced);


    /*   TARGETS   */
    public final Setting<SettingGroup> targets = new Setting<>("Targets", new SettingGroup(false, 0));
    public final Setting<Boolean> Players = new Setting<>("Players", true).addToGroup(targets);
    public final Setting<Boolean> Mobs = new Setting<>("Mobs", true).addToGroup(targets);
    public final Setting<Boolean> Animals = new Setting<>("Animals", true).addToGroup(targets);
    public final Setting<Boolean> Villagers = new Setting<>("Villagers", true).addToGroup(targets);
    public final Setting<Boolean> Slimes = new Setting<>("Slimes", true).addToGroup(targets);
    public final Setting<Boolean> hostiles = new Setting<>("Hostiles", true).addToGroup(targets);
    public final Setting<Boolean> onlyAngry = new Setting<>("OnlyAngryHostiles", true, v -> hostiles.getValue()).addToGroup(targets);
    public final Setting<Boolean> Projectiles = new Setting<>("Projectiles", true).addToGroup(targets);
    public final Setting<Boolean> ignoreInvisible = new Setting<>("IgnoreInvisibleEntities", false).addToGroup(targets);
    public final Setting<Boolean> ignoreNamed = new Setting<>("IgnoreNamed", false).addToGroup(targets);
    public final Setting<Boolean> ignoreTeam = new Setting<>("IgnoreTeam", false).addToGroup(targets);
    public final Setting<Boolean> ignoreCreative = new Setting<>("IgnoreCreative", true).addToGroup(targets);
    public final Setting<Boolean> ignoreNaked = new Setting<>("IgnoreNaked", false).addToGroup(targets);
    public final Setting<Boolean> ignoreShield = new Setting<>("AttackShieldingEntities", true).addToGroup(targets);

    public static class_1297 target;

    public float rotationYaw;
    public float rotationPitch;
    public float pitchAcceleration = 1f;
    
    // Thêm biến externalPause để tạm dừng Aura từ bên ngoài (VD: AutoCart)
    public boolean externalPause = false;

    private class_243 rotationPoint = class_243.field_1353;
    private class_243 rotationMotion = class_243.field_1353;

    private int hitTicks;
    private int trackticks;
    private boolean lookingAtHitbox;

    private final Timer delayTimer = new Timer();
    private final Timer pauseTimer = new Timer();

    public class_238 resolvedBox;
    static boolean wasTargeted = false;

    private double orbitAngle = 0;

    public Aura() {
        super("Aura", Category.COMBAT);
    }

    private float getRange(){
        return elytra.getValue() && mc.field_1724.method_6128() ? elytraAttackRange.getValue() : attackRange.getValue();
    }
    private float getWallRange(){
        return elytra.getValue() && mc.field_1724 != null && mc.field_1724.method_6128() ? elytraWallRange.getValue() : wallRange.getValue();
    }

    public void auraLogic() {
        // Kiểm tra externalPause - nếu true thì tạm dừng hoàn toàn
        if (externalPause) {
            return;
        }
        
        if (!haveWeapon()) {
            target = null;
            return;
        }

        handleKill();
        updateTarget();

        if (target == null) {
            return;
        }

        if (!mc.field_1690.field_1903.method_1434() && mc.field_1724.method_24828() && autoJump.getValue())
            mc.field_1724.method_6043();

        boolean readyForAttack;

        if (grimRayTrace.getValue()) {
            readyForAttack = autoCrit() && (lookingAtHitbox || skipRayTraceCheck());
            calcRotations(autoCrit());
        } else {
            calcRotations(autoCrit());
            readyForAttack = autoCrit() && (lookingAtHitbox || skipRayTraceCheck());
        }

        if (readyForAttack) {
            if (shieldBreaker(false))
                return;

            boolean[] playerState = preAttack();
            if (!(target instanceof class_1657 pl) || !(pl.method_6115() && pl.method_6079().method_7909() == class_1802.field_8255) || ignoreShield.getValue())
                attack();

            postAttack(playerState[0], playerState[1]);
        }
    }

    private boolean haveWeapon() {
        class_1792 handItem = mc.field_1724.method_6047().method_7909();
        if (onlyWeapon.getValue()) {
            if (switchMode.getValue() == Switch.None) {
                return handItem instanceof class_1829 || handItem instanceof class_1743 || handItem instanceof class_1835;
            } else {
                return (InventoryUtility.getSwordHotBar().found() || InventoryUtility.getAxeHotBar().found());
            }
        }
        return true;
    }

    private boolean skipRayTraceCheck() {
        return rotationMode.getValue() == Mode.None || rayTrace.getValue() == RayTrace.OFF
                || rotationMode.is(Mode.Grim)
                || (rotationMode.is(Mode.Interact) && (interactTicks.getValue() <= 1
                || mc.field_1687.method_20812(mc.field_1724, mc.field_1724.method_5829().method_1009(-0.25, 0.0, -0.25).method_989(0.0, 1, 0.0)).iterator().hasNext()));
    }

    public void attack() {
        Criticals.cancelCrit = true;
        ModuleManager.criticals.doCrit();
        int prevSlot = switchMethod();
        mc.field_1761.method_2918(mc.field_1724, target);
        Criticals.cancelCrit = false;
        swingHand();
        hitTicks = getHitTicks();
        if (prevSlot != -1)
            InventoryUtility.switchTo(prevSlot);
    }

    private boolean @NotNull [] preAttack() {
        boolean blocking = mc.field_1724.method_6115() && mc.field_1724.method_6030().method_7909().method_7853(mc.field_1724.method_6030()) == field_8949;
        if (blocking && unpressShield.getValue())
            sendPacket(new class_2846(class_2846.class_2847.field_12974, class_2338.field_10980, class_2350.field_11033));

        boolean sprint = Core.serverSprint;
        if (sprint && dropSprint.getValue())
            disableSprint();

        if (rotationMode.is(Mode.Grim))
            sendPacket(new class_2828.class_2830(mc.field_1724.method_23317(), mc.field_1724.method_23318(), mc.field_1724.method_23321(), rotationYaw, rotationPitch, mc.field_1724.method_24828()));

        return new boolean[]{blocking, sprint};
    }

    public void postAttack(boolean block, boolean sprint) {
        if (sprint && returnSprint.getValue() && dropSprint.getValue())
            enableSprint();

        if (block && unpressShield.getValue())
            sendSequencedPacket(id -> new class_2886(class_1268.field_5810, id, rotationYaw, rotationPitch));

        if (rotationMode.is(Mode.Grim))
            sendPacket(new class_2828.class_2830(mc.field_1724.method_23317(), mc.field_1724.method_23318(), mc.field_1724.method_23321(), mc.field_1724.method_36454(), mc.field_1724.method_36455(), mc.field_1724.method_24828()));
    }

    private void disableSprint() {
        mc.field_1724.method_5728(false);
        mc.field_1690.field_1867.method_23481(false);
        sendPacket(new class_2848(mc.field_1724, class_2848.class_2849.field_12985));
    }

    private void enableSprint() {
        mc.field_1724.method_5728(true);
        mc.field_1690.field_1867.method_23481(true);
        sendPacket(new class_2848(mc.field_1724, class_2848.class_2849.field_12981));
    }

    public void resolvePlayers() {
        if (resolver.not(Resolver.Off))
            for (class_1657 player : mc.field_1687.method_18456())
                if (player instanceof class_745)
                    ((IOtherClientPlayerEntity) player).resolve(resolver.getValue());
    }

    public void restorePlayers() {
        if (resolver.not(Resolver.Off))
            for (class_1657 player : mc.field_1687.method_18456())
                if (player instanceof class_745)
                    ((IOtherClientPlayerEntity) player).releaseResolver();
    }

    public void handleKill() {
        if (target instanceof class_1309 && (((class_1309) target).method_6032() <= 0 || ((class_1309) target).method_29504()))
            Managers.NOTIFICATION.publicity("Aura", isRu() ? "Цель успешно нейтрализована!" : "Target successfully neutralized!", 3, Notification.Type.SUCCESS);
    }

    private int switchMethod() {
        int prevSlot = -1;
        SearchInvResult swordResult = InventoryUtility.getSwordHotBar();
        if (swordResult.found() && switchMode.getValue() != Switch.None) {
            if (switchMode.getValue() == Switch.Silent)
                prevSlot = mc.field_1724.method_31548().field_7545;
            swordResult.switchTo();
        }

        return prevSlot;
    }

    private int getHitTicks() {
        return oldDelay.getValue().isEnabled() ? 1 + (int) (20f / random(minCPS.getValue(), maxCPS.getValue())) : (shouldRandomizeDelay() ? (int) MathUtility.random(11, 13) : attackTickLimit.getValue());
    }

    @EventHandler
    public void onUpdate(PlayerUpdateEvent e) {
        if (!pauseTimer.passedMs(1000))
            return;

        if (mc.field_1724.method_6115() && pauseWhileEating.getValue())
            return;
        if(pauseBaritone.getValue() && ThunderHack.baritone){
            boolean isTargeted = (target != null);
            if (isTargeted && !wasTargeted) {
                BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute("pause");
                wasTargeted = true;
            } else if (!isTargeted && wasTargeted) {
                BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute("resume");
                wasTargeted = false;
            }
        }

        resolvePlayers();
        auraLogic();
        restorePlayers();
        hitTicks--;
    }

    @EventHandler
    public void onSync(EventSync e) {
        if (!pauseTimer.passedMs(1000))
            return;

        if (mc.field_1724.method_6115() && pauseWhileEating.getValue())
            return;

        if (!haveWeapon())
            return;

        if (target != null && rotationMode.getValue() != Mode.None && rotationMode.getValue() != Mode.Grim) {
            mc.field_1724.method_36456(rotationYaw);
            mc.field_1724.method_36457(rotationPitch);
        } else {
            rotationYaw = mc.field_1724.method_36454();
            rotationPitch = mc.field_1724.method_36455();
        }

        if (oldDelay.getValue().isEnabled())
            if (minCPS.getValue() > maxCPS.getValue())
                minCPS.setValue(maxCPS.getValue());

        if (target != null && pullDown.getValue() && (mc.field_1724.method_6059(class_1294.field_5913) || !onlyJumpBoost.getValue()))
            mc.field_1724.method_5762(0f, -pullValue.getValue() / 1000f, 0f);
    }

    @EventHandler
    public void onPacketSend(PacketEvent.@NotNull Send e) {
        if (e.getPacket() instanceof class_2824 pie && Criticals.getInteractType(pie) != Criticals.InteractType.ATTACK && target != null)
            e.cancel();
    }

    @EventHandler
    public void onPacketReceive(PacketEvent.@NotNull Receive e) {
        if (e.getPacket() instanceof class_2663 status)
            if (status.method_11470() == 30 && status.method_11469(mc.field_1687) != null && target != null && status.method_11469(mc.field_1687) == target)
                Managers.NOTIFICATION.publicity("Aura", isRu() ? ("Успешно сломали щит игроку " + target.method_5477().getString()) : ("Succesfully destroyed " + target.method_5477().getString() + "'s shield"), 2, Notification.Type.SUCCESS);

        if (e.getPacket() instanceof class_2708 && tpDisable.getValue())
            disable(isRu() ? "Отключаю из-за телепортации!" : "Disabling due to tp!");

        if (e.getPacket() instanceof class_2663 pac && pac.method_11470() == 3 && pac.method_11469(mc.field_1687) == mc.field_1724 && deathDisable.getValue())
            disable(isRu() ? "Отключаю из-за смерти!" : "Disabling due to death!");
    }

    @Override
    public void onEnable() {
        target = null;
        lookingAtHitbox = false;
        rotationPoint = class_243.field_1353;
        rotationMotion = class_243.field_1353;
        rotationYaw = mc.field_1724.method_36454();
        rotationPitch = mc.field_1724.method_36455();
        orbitAngle = 0;
        delayTimer.reset();
    }

    private boolean autoCrit() {
        boolean reasonForSkipCrit =
                !smartCrit.getValue().isEnabled()
                        || mc.field_1724.method_31549().field_7479
                        || (mc.field_1724.method_6128() || ModuleManager.elytraPlus.isEnabled())
                        || mc.field_1724.method_6059(class_1294.field_5919)
                        || mc.field_1724.method_6059(class_1294.field_5906)
                        || Managers.PLAYER.isInWeb();

        if (hitTicks > 0)
            return false;

        if (pauseInInventory.getValue() && Managers.PLAYER.inInventory)
            return false;

        if (getAttackCooldown() < attackCooldown.getValue() && !oldDelay.getValue().isEnabled())
            return false;

        if (ModuleManager.criticals.isEnabled() && ModuleManager.criticals.mode.is(Criticals.Mode.Grim))
            return true;

        boolean mergeWithTargetStrafe = !ModuleManager.targetStrafe.isEnabled() || !ModuleManager.targetStrafe.jump.getValue();
        boolean mergeWithSpeed = !ModuleManager.speed.isEnabled() || mc.field_1724.method_24828();

        if (!mc.field_1690.field_1903.method_1434() && mergeWithTargetStrafe && mergeWithSpeed && !onlySpace.getValue() && !autoJump.getValue())
            return true;

        if (mc.field_1724.method_5771() || mc.field_1724.method_5869())
            return true;

        if (!mc.field_1690.field_1903.method_1434() && isAboveWater())
            return true;

        // я хз почему оно не критует когда фд больше 1.14
        if (mc.field_1724.field_6017 > 1 && mc.field_1724.field_6017 < 1.14)
            return false;

        if (!reasonForSkipCrit)
            return !mc.field_1724.method_24828() && mc.field_1724.field_6017 > (shouldRandomizeFallDistance() ? MathUtility.random(0.15f, 0.7f) : critFallDistance.getValue());
        return true;
    }

    private boolean shieldBreaker(boolean instant) { //todo - Actual value of parameter 'instant' is always 'false'
        int axeSlot = InventoryUtility.getAxe().slot();
        if (axeSlot == -1) return false;
        if (!shieldBreaker.getValue()) return false;
        if (!(target instanceof class_1657)) return false;
        if (!((class_1657) target).method_6115() && !instant) return false;
        if (((class_1657) target).method_6079().method_7909() != class_1802.field_8255 && ((class_1657) target).method_6047().method_7909() != class_1802.field_8255)
            return false;

        if (axeSlot >= 9) {
            mc.field_1761.method_2906(mc.field_1724.field_7512.field_7763, axeSlot, mc.field_1724.method_31548().field_7545, class_1713.field_7791, mc.field_1724);
            sendPacket(new class_2815(mc.field_1724.field_7512.field_7763));
            mc.field_1761.method_2918(mc.field_1724, target);
            swingHand();
            mc.field_1761.method_2906(mc.field_1724.field_7512.field_7763, axeSlot, mc.field_1724.method_31548().field_7545, class_1713.field_7791, mc.field_1724);
            sendPacket(new class_2815(mc.field_1724.field_7512.field_7763));
        } else {
            sendPacket(new class_2868(axeSlot));
            mc.field_1761.method_2918(mc.field_1724, target);
            swingHand();
            sendPacket(new class_2868(mc.field_1724.method_31548().field_7545));
        }
        hitTicks = 10;
        return true;
    }

    private void swingHand() {
        switch (attackHand.getValue()) {
            case OffHand -> mc.field_1724.method_6104(class_1268.field_5810);
            case MainHand -> mc.field_1724.method_6104(class_1268.field_5808);
        }
    }

    public boolean isAboveWater() {
        return mc.field_1724.method_5869() || mc.field_1687.method_8320(class_2338.method_49638(mc.field_1724.method_19538().method_1031(0, -0.4, 0))).method_26204() == class_2246.field_10382;
    }

    public float getAttackCooldownProgressPerTick() {
        return (float) (1.0 / mc.field_1724.method_45325(class_5134.field_23723) * (20.0 * ThunderHack.TICK_TIMER * (tpsSync.getValue() ? Managers.SERVER.getTPSFactor() : 1f)));
    }

    public float getAttackCooldown() {
        return class_3532.method_15363(((float) ((ILivingEntity) mc.field_1724).getLastAttackedTicks() + attackBaseTime.getValue()) / getAttackCooldownProgressPerTick(), 0.0F, 1.0F);
    }

    private void updateTarget() {
        class_1297 candidat = findTarget();

        if (target == null) {
            target = candidat;
            return;
        }

        if (sort.getValue() == Sort.FOV || !lockTarget.getValue())
            target = candidat;

        if (candidat instanceof class_1676)
            target = candidat;

        if (skipEntity(target))
            target = null;
    }

    private void calcRotations(boolean ready) {
        if (ready) {
            trackticks = (mc.field_1687.method_20812(mc.field_1724, mc.field_1724.method_5829().method_1009(-0.25, 0.0, -0.25).method_989(0.0, 1, 0.0)).iterator().hasNext() ? 1 : interactTicks.getValue());
        } else if (trackticks > 0) {
            trackticks--;
        }

        if (target == null)
            return;


        class_243 targetVec;

        if (mc.field_1724.method_6128() || ModuleManager.elytraPlus.isEnabled()) targetVec = target.method_33571();
        else targetVec = getLegitLook(target);

        if (targetVec == null)
            return;

        pitchAcceleration = Managers.PLAYER.checkRtx(rotationYaw, rotationPitch, getRange() + aimRange.getValue(), getRange() + aimRange.getValue(), rayTrace.getValue())
                ? aimedPitchStep.getValue() : pitchAcceleration < maxPitchStep.getValue() ? pitchAcceleration * pitchAccelerate.getValue() : maxPitchStep.getValue();

        float delta_yaw = method_15393((float) method_15338(Math.toDegrees(Math.atan2(targetVec.field_1350 - mc.field_1724.method_23321(), (targetVec.field_1352 - mc.field_1724.method_23317()))) - 90) - rotationYaw) + (wallsBypass.is(WallsBypass.V2) && !ready && !mc.field_1724.method_6057(target) ? 20 : 0);
        float delta_pitch = ((float) (-Math.toDegrees(Math.atan2(targetVec.field_1351 - (mc.field_1724.method_19538().field_1351 + mc.field_1724.method_18381(mc.field_1724.method_18376())), Math.sqrt(Math.pow((targetVec.field_1352 - mc.field_1724.method_23317()), 2) + Math.pow(targetVec.field_1350 - mc.field_1724.method_23321(), 2))))) - rotationPitch);

        float yawStep = rotationMode.getValue() != Mode.Track ? 360f : random(minYawStep.getValue(), maxYawStep.getValue());
        float pitchStep = rotationMode.getValue() != Mode.Track ? 180f : Managers.PLAYER.ticksElytraFlying > 5 ? 180 : (pitchAcceleration + random(-1f, 1f));

        if (ready)
            switch (accelerateOnHit.getValue()) {
                case Yaw -> yawStep = 180f;
                case Pitch -> pitchStep = 90f;
                case Both -> {
                    yawStep = 180f;
                    pitchStep = 90f;
                }
            }

        if (delta_yaw > 180)
            delta_yaw = delta_yaw - 180;

        float deltaYaw = class_3532.method_15363(class_3532.method_15379(delta_yaw), -yawStep, yawStep);

        float deltaPitch = class_3532.method_15363(delta_pitch, -pitchStep, pitchStep);

        float newYaw = rotationYaw + (delta_yaw > 0 ? deltaYaw : -deltaYaw);
        float newPitch = class_3532.method_15363(rotationPitch + deltaPitch, -90.0F, 90.0F);

        double gcdFix = (Math.pow(mc.field_1690.method_42495().method_41753() * 0.6 + 0.2, 3.0)) * 1.2;

        if (trackticks > 0 || rotationMode.getValue() == Mode.Track) {
            rotationYaw = (float) (newYaw - (newYaw - rotationYaw) % gcdFix);
            rotationPitch = (float) (newPitch - (newPitch - rotationPitch) % gcdFix);
        } else {
            rotationYaw = mc.field_1724.method_36454();
            rotationPitch = mc.field_1724.method_36455();
        }

        if (!rotationMode.is(Mode.Grim))
            ModuleManager.rotations.fixRotation = rotationYaw;
        lookingAtHitbox = Managers.PLAYER.checkRtx(rotationYaw, rotationPitch, getRange(), getWallRange(), rayTrace.getValue());
    }

    public void onRender3D(class_4587 stack) {
        if (!haveWeapon() || target == null)
            return;

        if ((resolver.is(Resolver.BackTrack) || resolverVisualisation.getValue()) && resolvedBox != null)
            Render3DEngine.OUTLINE_QUEUE.add(new Render3DEngine.OutlineAction(resolvedBox, HudEditor.getColor(0), 1));

        switch (esp.getValue()) {
            case CelkaPasta -> Render3DEngine.drawOldTargetEsp(stack, target);
            case NurikZapen -> CaptureMark.render(target);
            case ThunderHackV2 -> Render3DEngine.renderGhosts(espLength.getValue(), espFactor.getValue(), espShaking.getValue(), espAmplitude.getValue(), target);
            case ThunderHack -> Render3DEngine.drawTargetEsp(stack, target);
            case Orbit -> renderOrbitEsp(target);
        }

        if (clientLook.getValue() && rotationMode.getValue() != Mode.None) {
            mc.field_1724.method_36456((float) Render2DEngine.interpolate(mc.field_1724.field_5982, rotationYaw, Render3DEngine.getTickDelta()));
            mc.field_1724.method_36457((float) Render2DEngine.interpolate(mc.field_1724.field_6004, rotationPitch, Render3DEngine.getTickDelta()));
        }
    }

    private void renderOrbitEsp(class_1297 target) {
        orbitAngle += orbitSpeed.getValue() * 0.03;
        double cx = target.method_23317();
        double cy = target.method_23318() + target.method_5829().method_17940() / 2.0;
        double cz = target.method_23321();
        float r = orbitRadius.getValue();
        float size = orbitSize.getValue();
        int totalBeads = orbitCount.getValue() * 12;
        for (int i = 0; i < totalBeads; i++) {
            double angle = orbitAngle + (Math.PI * 2.0 / totalBeads) * i;
            float progress = (float) i / totalBeads;
            Color color = HudEditor.getColor((int) (progress * 360));
            int alpha = 180 - (int)(Math.abs(Math.sin(angle - orbitAngle)) * 80);
            Color fill = new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.max(60, alpha));
            double x = cx + Math.sin(angle) * r;
            double z = cz + Math.cos(angle) * r;
            float beadSize = size * (0.6f + 0.4f * progress);
            class_238 box = new class_238(x - beadSize, cy - beadSize, z - beadSize, x + beadSize, cy + beadSize, z + beadSize);
            Render3DEngine.FILLED_QUEUE.add(new Render3DEngine.FillAction(box, fill));
            if (i % 3 == 0)
                Render3DEngine.OUTLINE_QUEUE.add(new Render3DEngine.OutlineAction(box, color, 1f));
        }
    }

    @Override
    public void onDisable() {
        target = null;
        externalPause = false; // Reset externalPause khi tắt module
    }

    public float getSquaredRotateDistance() {
        float dst = getRange();
        dst += aimRange.getValue();
        if ((mc.field_1724.method_6128() || ModuleManager.elytraPlus.isEnabled()) && target != null) dst += 4f;
        if (ModuleManager.strafe.isEnabled()) dst += 4f;
        if (rotationMode.getValue() != Mode.Track || rayTrace.getValue() == RayTrace.OFF)
            dst = getRange();

        return dst * dst;
    }

    /*
     * Эта хуеверть основанна на приципе "DVD Logo"
     * У нас есть точка и "коробка" (хитбокс цели)
     * Точка летает внутри коробки и отталкивается от стенок с рандомной скоростью и легким джиттером
     * Также выбирает лучшую дистанцию для удара, то есть считает не от центра до центра, а от наших глаз до достигаемых точек хитбокса цели
     * Со стороны не сильно заметно что ты играешь с киллкой, в отличие от аур семейства Wexside
     */

    public class_243 getLegitLook(class_1297 target) {

        float minMotionXZ = 0.003f;
        float maxMotionXZ = 0.03f;

        float minMotionY = 0.001f;
        float maxMotionY = 0.03f;

        double lenghtX = target.method_5829().method_17939();
        double lenghtY = target.method_5829().method_17940();
        double lenghtZ = target.method_5829().method_17941();


        // Задаем начальную скорость точки
        if (rotationMotion.equals(class_243.field_1353))
            rotationMotion = new class_243(random(-0.05f, 0.05f), random(-0.05f, 0.05f), random(-0.05f, 0.05f));

        rotationPoint = rotationPoint.method_1019(rotationMotion);

        // Сталкиваемся с хитбоксом по X
        if (rotationPoint.field_1352 >= (lenghtX - 0.05) / 2f)
            rotationMotion = new class_243(-random(minMotionXZ, maxMotionXZ), rotationMotion.method_10214(), rotationMotion.method_10215());

        // Сталкиваемся с хитбоксом по Y
        if (rotationPoint.field_1351 >= lenghtY)
            rotationMotion = new class_243(rotationMotion.method_10216(), -random(minMotionY, maxMotionY), rotationMotion.method_10215());

        // Сталкиваемся с хитбоксом по Z
        if (rotationPoint.field_1350 >= (lenghtZ - 0.05) / 2f)
            rotationMotion = new class_243(rotationMotion.method_10216(), rotationMotion.method_10214(), -random(minMotionXZ, maxMotionXZ));

        // Сталкиваемся с хитбоксом по -X
        if (rotationPoint.field_1352 <= -(lenghtX - 0.05) / 2f)
            rotationMotion = new class_243(random(minMotionXZ, 0.03f), rotationMotion.method_10214(), rotationMotion.method_10215());

        // Сталкиваемся с хитбоксом по -Y
        if (rotationPoint.field_1351 <= 0.05)
            rotationMotion = new class_243(rotationMotion.method_10216(), random(minMotionY, maxMotionY), rotationMotion.method_10215());

        // Сталкиваемся с хитбоксом по -Z
        if (rotationPoint.field_1350 <= -(lenghtZ - 0.05) / 2f)
            rotationMotion = new class_243(rotationMotion.method_10216(), rotationMotion.method_10214(), random(minMotionXZ, maxMotionXZ));

        // Добавляем джиттер
        rotationPoint.method_1031(random(-0.03f, 0.03f), 0f, random(-0.03f, 0.03f));

        if (!mc.field_1724.method_6057(target)) {
            // Если мы используем обход ударов через стену V1 и наша цель за стеной, то целимся в верхушку хитбокса т.к. матриксу поебать
            if (Objects.requireNonNull(wallsBypass.getValue()) == WallsBypass.V1) {
                return target.method_19538().method_1031(random(-0.15, 0.15), lenghtY, random(-0.15, 0.15));
            }
        }

        float[] rotation;

        // Если мы перестали смотреть на цель
        if (!Managers.PLAYER.checkRtx(rotationYaw, rotationPitch, getRange(), getWallRange(), rayTrace.getValue())) {
            float[] rotation1 = Managers.PLAYER.calcAngle(target.method_19538().method_1031(0, target.method_18381(target.method_18376()) / 2f, 0));

            // Проверяем видимость центра игрока
            if (PlayerUtility.squaredDistanceFromEyes(target.method_19538().method_1031(0, target.method_18381(target.method_18376()) / 2f, 0)) <= attackRange.getPow2Value()
                    && Managers.PLAYER.checkRtx(rotation1[0], rotation1[1], getRange(), 0, rayTrace.getValue())) {
                // наводим на центр
                rotationPoint = new class_243(random(-0.1f, 0.1f), target.method_18381(target.method_18376()) / (random(1.8f, 2.5f)), random(-0.1f, 0.1f));
            } else {
                // Сканим хитбокс на видимую точку
                float halfBox = (float) (lenghtX / 2f);

                for (float x1 = -halfBox; x1 <= halfBox; x1 += 0.05f) {
                    for (float z1 = -halfBox; z1 <= halfBox; z1 += 0.05f) {
                        for (float y1 = 0.05f; y1 <= target.method_5829().method_17940(); y1 += 0.15f) {

                            class_243 v1 = new class_243(target.method_23317() + x1, target.method_23318() + y1, target.method_23321() + z1);

                            // Скипаем, если вне досягаемости
                            if (PlayerUtility.squaredDistanceFromEyes(v1) > attackRange.getPow2Value()) continue;

                            rotation = Managers.PLAYER.calcAngle(v1);
                            if (Managers.PLAYER.checkRtx(rotation[0], rotation[1], getRange(), 0, rayTrace.getValue())) {
                                // Наводимся, если видим эту точку
                                rotationPoint = new class_243(x1, y1, z1);
                                break;
                            }
                        }
                    }
                }
            }
        }
        return target.method_19538().method_1019(rotationPoint);
    }

    public boolean isInRange(class_1297 target) {

        if (PlayerUtility.squaredDistanceFromEyes(target.method_19538().method_1031(0, target.method_18381(target.method_18376()), 0)) > getSquaredRotateDistance() + 4) {
            return false;
        }

        float[] rotation;
        float halfBox = (float) (target.method_5829().method_17939() / 2f);

        // уменьшил частоту выборки
        for (float x1 = -halfBox; x1 <= halfBox; x1 += 0.15f) {
            for (float z1 = -halfBox; z1 <= halfBox; z1 += 0.15f) {
                for (float y1 = 0.05f; y1 <= target.method_5829().method_17940(); y1 += 0.25f) {
                    if (PlayerUtility.squaredDistanceFromEyes(new class_243(target.method_23317() + x1, target.method_23318() + y1, target.method_23321() + z1)) > getSquaredRotateDistance())
                        continue;

                    rotation = Managers.PLAYER.calcAngle(new class_243(target.method_23317() + x1, target.method_23318() + y1, target.method_23321() + z1));
                    if (Managers.PLAYER.checkRtx(rotation[0], rotation[1], (float) Math.sqrt(getSquaredRotateDistance()), getWallRange(), rayTrace.getValue())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public class_1297 findTarget() {
        List<class_1309> first_stage = new CopyOnWriteArrayList<>();
        for (class_1297 ent : mc.field_1687.method_18112()) {
            if ((ent instanceof class_1678 || ent instanceof class_1674)
                    && ent.method_5805()
                    && isInRange(ent)
                    && Projectiles.getValue()) {
                return ent;
            }
            if (skipEntity(ent)) continue;
            if (!(ent instanceof class_1309)) continue;
            first_stage.add((class_1309) ent);
        }

        return switch (sort.getValue()) {
            case LowestDistance ->
                    first_stage.stream().min(Comparator.comparing(e -> (mc.field_1724.method_5707(e.method_19538())))).orElse(null);

            case HighestDistance ->
                    first_stage.stream().max(Comparator.comparing(e -> (mc.field_1724.method_5707(e.method_19538())))).orElse(null);

            case FOV -> first_stage.stream().min(Comparator.comparing(this::getFOVAngle)).orElse(null);

            case LowestHealth ->
                    first_stage.stream().min(Comparator.comparing(e -> (e.method_6032() + e.method_6067()))).orElse(null);

            case HighestHealth ->
                    first_stage.stream().max(Comparator.comparing(e -> (e.method_6032() + e.method_6067()))).orElse(null);

            case LowestDurability -> first_stage.stream().min(Comparator.comparing(e -> {
                        float v = 0;
                        for (class_1799 armor : e.method_5661())
                            if (armor != null && !armor.method_7909().equals(class_1802.field_8162)) {
                                v += ((armor.method_7936() - armor.method_7919()) / (float) armor.method_7936());
                            }
                        return v;
                    }
            )).orElse(null);

            case HighestDurability -> first_stage.stream().max(Comparator.comparing(e -> {
                        float v = 0;
                        for (class_1799 armor : e.method_5661())
                            if (armor != null && !armor.method_7909().equals(class_1802.field_8162)) {
                                v += ((armor.method_7936() - armor.method_7919()) / (float) armor.method_7936());
                            }
                        return v;
                    }
            )).orElse(null);
        };
    }

    private boolean skipEntity(class_1297 entity) {
        if (isBullet(entity)) return false;
        if (!(entity instanceof class_1309 ent)) return true;
        if (ent.method_29504() || !entity.method_5805()) return true;
        if (entity instanceof class_1531) return true;
        if (entity instanceof class_1451) return true;
        if (skipNotSelected(entity)) return true;
        if (!InteractionUtility.isVecInFOV(ent.method_19538(), fov.getValue())) return true;

        if (entity instanceof class_1657 player) {
            if (ModuleManager.antiBot.isEnabled() && AntiBot.bots.contains(entity))
                return true;
            if (player == mc.field_1724 || Managers.FRIEND.isFriend(player))
                return true;
            if (player.method_7337() && ignoreCreative.getValue())
                return true;
            if (player.method_6096() == 0 && ignoreNaked.getValue())
                return true;
            if (player.method_5767() && ignoreInvisible.getValue())
                return true;
            if (player.method_22861() == mc.field_1724.method_22861() && ignoreTeam.getValue() && mc.field_1724.method_22861() != 16777215)
                return true;
        }

        return !isInRange(entity) || (entity.method_16914() && ignoreNamed.getValue());
    }

    private boolean isBullet(class_1297 entity) {
        return (entity instanceof class_1678 || entity instanceof class_1674)
                && entity.method_5805()
                && PlayerUtility.squaredDistanceFromEyes(entity.method_19538()) < getSquaredRotateDistance()
                && Projectiles.getValue();
    }

    private boolean skipNotSelected(class_1297 entity) {
        if (entity instanceof class_1621 && !Slimes.getValue()) return true;
        if (entity instanceof class_1588 he) {
            if (!hostiles.getValue())
                return true;

            if (onlyAngry.getValue())
                return !he.method_7076(mc.field_1724);
        }

        if (entity instanceof class_1657 && !Players.getValue()) return true;
        if (entity instanceof class_1646 && !Villagers.getValue()) return true;
        if (entity instanceof class_1308 && !Mobs.getValue()) return true;
        return entity instanceof class_1429 && !Animals.getValue();
    }

    private float getFOVAngle(@NotNull class_1309 e) {
        double difX = e.method_23317() - mc.field_1724.method_23317();
        double difZ = e.method_23321() - mc.field_1724.method_23321();
        float yaw = (float) class_3532.method_15338(Math.toDegrees(Math.atan2(difZ, difX)) - 90.0);
        return Math.abs(yaw - class_3532.method_15393(mc.field_1724.method_36454()));
    }

    public void pause() {
        pauseTimer.reset();
    }

    private boolean shouldRandomizeDelay() {
        return randomHitDelay.getValue() && (mc.field_1724.method_24828() || mc.field_1724.field_6017 < 0.12f || mc.field_1724.method_5681() || mc.field_1724.method_6128());
    }

    private boolean shouldRandomizeFallDistance() {
        return randomHitDelay.getValue() && !shouldRandomizeDelay();
    }

    public static class Position {
        private double x, y, z;
        private int ticks;

        public Position(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public boolean shouldRemove() {
            return ticks++ > ModuleManager.aura.backTicks.getValue();
        }

        public double getX() {
            return x;
        }

        public double getY() {
            return y;
        }

        public double getZ() {
            return z;

        }
    }

    public enum RayTrace {
        OFF, OnlyTarget, AllEntities
    }

    public enum Sort {
        LowestDistance, HighestDistance, LowestHealth, HighestHealth, LowestDurability, HighestDurability, FOV
    }

    public enum Switch {
        Normal, None, Silent
    }

    public enum Resolver {
        Off, Advantage, Predictive, BackTrack
    }

    public enum Mode {
        Interact, Track, Grim, None
    }

    public enum AttackHand {
        MainHand, OffHand, None
    }

    public enum ESP {
        Off, ThunderHack, NurikZapen, CelkaPasta, ThunderHackV2, Orbit
    }

    public enum AccelerateOnHit {
        Off, Yaw, Pitch, Both
    }

    public enum WallsBypass {
        Off, V1, V2
    }
}