package net.ccbluex.liquidbounce.features.module.modules.combat;

import net.ccbluex.liquidbounce.RedBone;
import net.ccbluex.liquidbounce.api.minecraft.client.IMinecraft;
import net.ccbluex.liquidbounce.api.minecraft.util.IAxisAlignedBB;
import net.ccbluex.liquidbounce.event.EventTarget;
import net.ccbluex.liquidbounce.event.Render3DEvent;
import net.ccbluex.liquidbounce.event.TickEvent;
import net.ccbluex.liquidbounce.event.UpdateEvent;
import net.ccbluex.liquidbounce.features.module.Module;
import net.ccbluex.liquidbounce.features.module.ModuleCategory;
import net.ccbluex.liquidbounce.features.module.ModuleInfo;
import net.ccbluex.liquidbounce.features.module.modules.misc.Teams;
import net.ccbluex.liquidbounce.utils.MinecraftInstance;
import net.ccbluex.liquidbounce.utils.Rotation;
import net.ccbluex.liquidbounce.utils.RotationUtils;
import net.ccbluex.liquidbounce.utils.render.RenderUtils;
import net.ccbluex.liquidbounce.utils.timer.MSTimer;
import net.ccbluex.liquidbounce.value.BoolValue;
import net.ccbluex.liquidbounce.value.FloatValue;
import net.ccbluex.liquidbounce.value.IntegerValue;
import net.ccbluex.liquidbounce.value.ListValue;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemFood;
import net.minecraft.network.play.client.CPacketUseEntity;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import org.apache.commons.lang3.RandomUtils;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

@ModuleInfo(name = "Aura", description = "Auto Attack.", category = ModuleCategory.COMBAT)
public class Aura extends Module {
    IntegerValue maxCPSValue = new IntegerValue("MaxCPS", 8, 1, 20);
    IntegerValue minCPSValue = new IntegerValue("MinCPS", 8, 1, 20);
    ListValue getCPSMode = new ListValue("GetCPSMode", new String[]{"Random", "UP"}, "UP");
    FloatValue rangeValue = new FloatValue("Range", 3f, 3f, 6.0f);
    FloatValue throughWallsRangeValue = new FloatValue("ThroughWallsRange", 3f, 3f, 6.0f);
    ListValue targetMode = new ListValue("TargetMode", new String[]{"Single", "Multi"}, "Single");
    BoolValue noFoodValue = new BoolValue("NoFood", true);
    ListValue rotationModeValue = new ListValue("RotationMode", new String[]{"Normal", "None"}, "Hyt");
    BoolValue silentRotationValue = new BoolValue("SilentRotation",true);
    FloatValue maxTurnSpeedValue = new FloatValue("MaxRotationSpeed", 180f, 1f, 180f);
    FloatValue minTurnSpeedValueValue = new FloatValue("MinRotationSpeed", 180f, 1f, 180f);
    IntegerValue rotationKeepLengthValue = new IntegerValue("RotationKeepLength", 0, 0, 20);
    BoolValue predictValue = new BoolValue("Predict", true);
    FloatValue maxPredictSize = new FloatValue("MaxPredictSize", 1f, 0.1f, 5f);
    FloatValue minPredictSize = new FloatValue("MinPredictSize", 1f, 0.1f, 5f);
    BoolValue outborderValue = new BoolValue("Outborder", false);
    BoolValue randomCenterValue = new BoolValue("RandomCenter", true);
    BoolValue noAttackingValue = new BoolValue("NoAttacking", true);
    BoolValue swingValue = new BoolValue("Swing", true);
    BoolValue teamsValue = new BoolValue("Teams",true);

    List<Entity> targets = new ArrayList<>();
    Minecraft mc = mc2;
    IMinecraft mc3 = MinecraftInstance.mc;
    int attacks;
    MSTimer attackTimer;

    @EventTarget
    void onTick(TickEvent event) {
        if (minCPSValue.get() > maxCPSValue.get()) maxCPSValue.set(minCPSValue.get());
    }

    @Override
    public void onDisable() {
        targets.clear();
        attackTimer = null;
    }

    @Override
    public void onEnable() {
        targets.clear();
        attackTimer = new MSTimer();
    }

    @EventTarget
    void onU(UpdateEvent e) {
        if (noFoodValue.get() && mc.player.isHandActive() && mc.player.getHeldItem(EnumHand.MAIN_HAND).getItem() instanceof ItemFood) return;
        getTargets();
        attackTargets();
        targets.removeIf(entity -> entity.getDistance(mc.player) > rangeValue.get() || entity.isDead);
    }

    @EventTarget
    void onR3D(Render3DEvent e) {
        if (targets.isEmpty()) return;

        if (targetMode.get().equals("Single")) {
            RenderUtils.drawPlatform(targets.get(0), new Color(37, 126, 255, 70));
        }

        if (targetMode.get().equals("Multi")) {
            for (Entity entity : targets) {
                RenderUtils.drawPlatform(entity, new Color(37, 126, 255, 70));
            }
        }
    }

    void getTargets() {
        for (Entity entity : mc.world.loadedEntityList) {
            if (!entity.isDead && entity != mc.player && entity.getDistance(mc.player) <= rangeValue.get() || (!RedBone.moduleManager.getModule(Teams.class).isInYourTeam(entity) && teamsValue.get()))
                targets.add(entity);
        }
    }

    void attackEntity(Entity entity) {
        if(!attackTimer.hasTimePassed(1000 / getCPS())) return;
        if(swingValue.get()) mc.player.swingArm(EnumHand.MAIN_HAND);
        mc.getConnection().sendPacket(new CPacketUseEntity(entity));
        attacks++;
    }

    void attackTargets() {
        if (targets.isEmpty() || (noAttackingValue.get() && mc.player.getAttackingEntity() != null)) return;
        if (targetMode.get().equals("Single")) {
            attackEntity(targets.get(0));
            if(!rotationModeValue.get().equals("None")) doRotation(targets.get(0));
        }

        if (targetMode.get().equals("Multi")) {
            for (Entity entity : targets) {
                attackEntity(entity);
                if(!rotationModeValue.get().equals("None")) doRotation(entity);
            }
        }
    }

    void doRotation(Entity entity) {

        if (rotationModeValue.get().equals("Normal")) {
            AxisAlignedBB boundingBox = entity.getEntityBoundingBox();
            if (maxTurnSpeedValue.get() <= 0F) return;
            if (predictValue.get()) {
                boundingBox = boundingBox.offset(
                        (entity.posX - entity.prevPosX) * RandomUtils.nextFloat(minPredictSize.get(), maxPredictSize.get()),
                        (entity.posY - entity.prevPosY) * RandomUtils.nextFloat(minPredictSize.get(), maxPredictSize.get()),
                        (entity.posZ - entity.prevPosZ) * RandomUtils.nextFloat(minPredictSize.get(), maxPredictSize.get())
                );
            }

            if (RotationUtils.lockView((IAxisAlignedBB) boundingBox, outborderValue.get(), randomCenterValue.get(), predictValue.get(), mc.player.getDistance(entity) < throughWallsRangeValue.get(), rangeValue.get()) == null)
                return;

            Rotation limitedRotation = RotationUtils.limitAngleChange(RotationUtils.serverRotation, RotationUtils.getNewRotations(RotationUtils.getCenter((IAxisAlignedBB) entity.getEntityBoundingBox()), false),
                    (float) (Math.random() * (maxTurnSpeedValue.get() - minTurnSpeedValueValue.get()) + minTurnSpeedValueValue.get()));

            if (silentRotationValue.get())
                RotationUtils.setTargetRotation(limitedRotation, rotationKeepLengthValue.get());
            else
                limitedRotation.toPlayer(mc3.getThePlayer());

        }
    }

    int getCPS() {
        int maxCPS = maxCPSValue.get();
        int minCPS = minCPSValue.get();

        int cps = 0;
        switch (getCPSMode.get()){
            case "Random" :
                cps = ((int) (Math.random() * (maxCPS - minCPS + 1)) + minCPS);
                break;
            case "UP" :
                int upCPS = minCPS + attacks;
                cps = Math.min(upCPS, maxCPS);
            break;

        }

        return cps;

    }


}
