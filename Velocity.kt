package net.ccbluex.liquidbounce.features.module.modules.combat

import net.ccbluex.liquidbounce.RedBone
import net.ccbluex.liquidbounce.api.minecraft.network.play.client.ICPacketUseEntity
import net.ccbluex.liquidbounce.event.*
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.features.module.ModuleCategory
import net.ccbluex.liquidbounce.features.module.ModuleInfo
import net.ccbluex.liquidbounce.injection.backend.unwrap
import net.ccbluex.liquidbounce.utils.timer.MSTimer
import net.ccbluex.liquidbounce.value.BoolValue
import net.ccbluex.liquidbounce.value.FloatValue
import net.ccbluex.liquidbounce.value.IntegerValue
import net.ccbluex.liquidbounce.value.ListValue
import net.minecraft.client.settings.GameSettings
import net.minecraft.network.play.client.CPacketEntityAction
import net.minecraft.network.play.client.CPacketUseEntity
import net.minecraft.network.play.server.SPacketEntityVelocity
import net.minecraft.network.play.server.SPacketExplosion
import net.minecraft.util.EnumHand

@ModuleInfo(
    name = "Velocity",
    description = "Allows you to modify the amount of knockback you take.",
    category = ModuleCategory.COMBAT
)
class Velocity : Module() {

    /**
     * OPTIONS
     */
    val modeValue = ListValue("Mode", arrayOf("Simple", "Jump","Hyt"), "Hyt")

    //Simple
    private val horizontalValue = FloatValue("Horizontal", 0F, 0F, 1F).displayable { modeValue.get() == "Simple" }
    private val verticalValue = FloatValue("Vertical", 0F, 0F, 1F).displayable { modeValue.get() == "Simple" }


    // Hyt
    private val packetSprintValue = BoolValue("PacketSprint", true).displayable { modeValue.get() == "Hyt" }
    private val repeatValue = IntegerValue("Repeat", 8, 1, 12).displayable { modeValue.get() == "Hyt" }

    private var lag = false
    private var press = false

    /**
     * VALUES
     */
    private var velocityTimer = MSTimer()
    private var velocityInput = false
    var x = 0.0
    var y = -0.1
    var z = 0.0
    override val tag: String
        get() = modeValue.get()

    override fun onDisable() {
        mc.thePlayer?.speedInAir = 0.02F
        velocityInput = false
        mc.timer.timerSpeed = 1f
        press = false
    }

    @EventTarget
    fun onUpdate(event: UpdateEvent) {
        if (mc.thePlayer!!.isInWater || mc.thePlayer!!.isInLava || mc.thePlayer!!.isInWeb || mc.thePlayer == null)
            return

        when (modeValue.get().toLowerCase()) {
            "jump" -> {
                if (!mc.thePlayer!!.onGround && press) {
                    mc.gameSettings.keyBindJump.pressed = GameSettings.isKeyDown(mc2.gameSettings.keyBindJump)
                    press = false
                }
            }

            "hyt" -> {

                if (!mc.thePlayer!!.serverSprintState && packetSprintValue.get()) {
                    mc2.player.connection.sendPacket(
                        CPacketEntityAction(
                            mc2.player, CPacketEntityAction.Action.START_SPRINTING
                        )
                    )
                }

                repeat(repeatValue.get()) {
                    mc2.player.connection.sendPacket(
                        CPacketUseEntity(
                            RedBone.moduleManager.getModule(KillAura::class.java).target!!.unwrap(),
                            EnumHand.MAIN_HAND
                        )
                    )
                    mc.netHandler.addToSendQueue(
                        classProvider.createCPacketUseEntity(
                            RedBone.moduleManager.getModule(
                                KillAura::class.java
                            ).target!!.asEntityLivingBase(), ICPacketUseEntity.WAction.ATTACK
                        )
                    )
                    mc.netHandler.addToSendQueue(classProvider.createCPacketAnimation())
                }

                mc2.player.motionX *= 0.077760000
                mc2.player.motionZ *= 0.077760000
            }
        }
    }

    @EventTarget
    fun onPacket(event: PacketEvent) {
        val packet = event.packet.unwrap()

        if (packet is SPacketEntityVelocity) {
            if (mc.thePlayer == null || (mc.theWorld?.getEntityByID(packet.entityID) ?: return) != mc.thePlayer)
                return

            velocityTimer.reset()

            x = mc.thePlayer!!.posX
            y = mc.thePlayer!!.posY
            z = mc.thePlayer!!.posZ

            when (modeValue.get().toLowerCase()) {
                "simple" -> {
                    val horizontal = horizontalValue.get()
                    val vertical = verticalValue.get()

                    if (horizontal == 0F && vertical == 0F)
                        event.cancelEvent()

                    packet.motionX = (packet.getMotionX() * horizontal).toInt()
                    packet.motionY = (packet.getMotionY() * vertical).toInt()
                    packet.motionZ = (packet.getMotionZ() * horizontal).toInt()
                }

                "jump" -> {
                    press = true
                }
            }
        }

        if (packet is SPacketExplosion) {
            // TODO: Support velocity for explosions
            event.cancelEvent()
        }
    }

    @EventTarget
    fun onMotion(event: MotionEvent) {
        lag = event.eventState == EventState.PRE
    }

}
