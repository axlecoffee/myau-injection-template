package coffee.axle.myauinjectiontemplate.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;
import org.lwjgl.opengl.GL11;

/**
 * Static renderer for TestModule - handles both mixin and direct rendering
 * paths
 */
public class TestModuleRenderer {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private static boolean mixinEnabled = false;
    private static boolean moduleEnabled = false;

    public static void setMixinEnabled(boolean enabled) {
        mixinEnabled = enabled;
    }

    public static void setModuleEnabled(boolean enabled) {
        moduleEnabled = enabled;
    }

    public static boolean isMixinEnabled() {
        return mixinEnabled;
    }

    public static boolean isModuleEnabled() {
        return moduleEnabled;
    }

    /**
     * Called from mixin - renders GREEN box when mixin path is active
     */
    public static void renderMixinBox(float partialTicks) {
        if (!moduleEnabled || !mixinEnabled) {
            return;
        }
        if (mc.thePlayer == null || mc.theWorld == null) {
            return;
        }

        AxisAlignedBB renderBox = calculateBoxPosition(partialTicks);

        // Green box for mixin rendering
        drawFilledBox(renderBox, 0.0f, 0.8f, 0.2f, 0.4f);
        drawOutlinedBox(renderBox, 0.2f, 1.0f, 0.4f, 1.0f);
    }

    /**
     * Called from Forge event - renders PURPLE box when direct path is active
     */
    public static void renderDirectBox(float partialTicks) {
        if (!moduleEnabled || mixinEnabled) {
            return;
        }
        if (mc.thePlayer == null || mc.theWorld == null) {
            return;
        }

        AxisAlignedBB renderBox = calculateBoxPosition(partialTicks);

        // Purple box for direct rendering
        drawFilledBox(renderBox, 0.5f, 0.0f, 0.8f, 0.4f);
        drawOutlinedBox(renderBox, 0.8f, 0.2f, 1.0f, 1.0f);
    }

    private static AxisAlignedBB calculateBoxPosition(float partialTicks) {
        Entity player = mc.thePlayer;

        // Get player look direction
        Vec3 lookVec = player.getLookVec();

        // Calculate interpolated player position
        double playerX = player.lastTickPosX + (player.posX - player.lastTickPosX) * partialTicks;
        double playerY = player.lastTickPosY + (player.posY - player.lastTickPosY) * partialTicks;
        double playerZ = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * partialTicks;

        // Position 3 blocks in front
        double boxX = playerX + lookVec.xCoord * 3.0;
        double boxY = playerY + lookVec.yCoord * 3.0 + player.getEyeHeight();
        double boxZ = playerZ + lookVec.zCoord * 3.0;

        // Create bounding box (1x1x1 cube centered at position)
        double size = 0.5;
        AxisAlignedBB box = new AxisAlignedBB(
                boxX - size, boxY - size, boxZ - size,
                boxX + size, boxY + size, boxZ + size);

        // Offset by render position
        double renderX = mc.getRenderManager().viewerPosX;
        double renderY = mc.getRenderManager().viewerPosY;
        double renderZ = mc.getRenderManager().viewerPosZ;

        return box.offset(-renderX, -renderY, -renderZ);
    }

    public static void drawFilledBox(AxisAlignedBB box, float red, float green, float blue, float alpha) {
        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GlStateManager.disableDepth();
        GlStateManager.disableCull();

        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer worldRenderer = tessellator.getWorldRenderer();

        worldRenderer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);

        // Bottom face
        worldRenderer.pos(box.minX, box.minY, box.minZ).color(red, green, blue, alpha).endVertex();
        worldRenderer.pos(box.maxX, box.minY, box.minZ).color(red, green, blue, alpha).endVertex();
        worldRenderer.pos(box.maxX, box.minY, box.maxZ).color(red, green, blue, alpha).endVertex();
        worldRenderer.pos(box.minX, box.minY, box.maxZ).color(red, green, blue, alpha).endVertex();

        // Top face
        worldRenderer.pos(box.minX, box.maxY, box.minZ).color(red, green, blue, alpha).endVertex();
        worldRenderer.pos(box.minX, box.maxY, box.maxZ).color(red, green, blue, alpha).endVertex();
        worldRenderer.pos(box.maxX, box.maxY, box.maxZ).color(red, green, blue, alpha).endVertex();
        worldRenderer.pos(box.maxX, box.maxY, box.minZ).color(red, green, blue, alpha).endVertex();

        // North face
        worldRenderer.pos(box.minX, box.minY, box.minZ).color(red, green, blue, alpha).endVertex();
        worldRenderer.pos(box.minX, box.maxY, box.minZ).color(red, green, blue, alpha).endVertex();
        worldRenderer.pos(box.maxX, box.maxY, box.minZ).color(red, green, blue, alpha).endVertex();
        worldRenderer.pos(box.maxX, box.minY, box.minZ).color(red, green, blue, alpha).endVertex();

        // South face
        worldRenderer.pos(box.minX, box.minY, box.maxZ).color(red, green, blue, alpha).endVertex();
        worldRenderer.pos(box.maxX, box.minY, box.maxZ).color(red, green, blue, alpha).endVertex();
        worldRenderer.pos(box.maxX, box.maxY, box.maxZ).color(red, green, blue, alpha).endVertex();
        worldRenderer.pos(box.minX, box.maxY, box.maxZ).color(red, green, blue, alpha).endVertex();

        // West face
        worldRenderer.pos(box.minX, box.minY, box.minZ).color(red, green, blue, alpha).endVertex();
        worldRenderer.pos(box.minX, box.minY, box.maxZ).color(red, green, blue, alpha).endVertex();
        worldRenderer.pos(box.minX, box.maxY, box.maxZ).color(red, green, blue, alpha).endVertex();
        worldRenderer.pos(box.minX, box.maxY, box.minZ).color(red, green, blue, alpha).endVertex();

        // East face
        worldRenderer.pos(box.maxX, box.minY, box.minZ).color(red, green, blue, alpha).endVertex();
        worldRenderer.pos(box.maxX, box.maxY, box.minZ).color(red, green, blue, alpha).endVertex();
        worldRenderer.pos(box.maxX, box.maxY, box.maxZ).color(red, green, blue, alpha).endVertex();
        worldRenderer.pos(box.maxX, box.minY, box.maxZ).color(red, green, blue, alpha).endVertex();

        tessellator.draw();

        GlStateManager.enableCull();
        GlStateManager.enableDepth();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    public static void drawOutlinedBox(AxisAlignedBB box, float red, float green, float blue, float alpha) {
        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GlStateManager.disableDepth();
        GL11.glLineWidth(2.0f);

        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer worldRenderer = tessellator.getWorldRenderer();

        worldRenderer.begin(GL11.GL_LINE_STRIP, DefaultVertexFormats.POSITION_COLOR);

        // Bottom face
        worldRenderer.pos(box.minX, box.minY, box.minZ).color(red, green, blue, alpha).endVertex();
        worldRenderer.pos(box.maxX, box.minY, box.minZ).color(red, green, blue, alpha).endVertex();
        worldRenderer.pos(box.maxX, box.minY, box.maxZ).color(red, green, blue, alpha).endVertex();
        worldRenderer.pos(box.minX, box.minY, box.maxZ).color(red, green, blue, alpha).endVertex();
        worldRenderer.pos(box.minX, box.minY, box.minZ).color(red, green, blue, alpha).endVertex();

        tessellator.draw();

        worldRenderer.begin(GL11.GL_LINE_STRIP, DefaultVertexFormats.POSITION_COLOR);

        // Top face
        worldRenderer.pos(box.minX, box.maxY, box.minZ).color(red, green, blue, alpha).endVertex();
        worldRenderer.pos(box.maxX, box.maxY, box.minZ).color(red, green, blue, alpha).endVertex();
        worldRenderer.pos(box.maxX, box.maxY, box.maxZ).color(red, green, blue, alpha).endVertex();
        worldRenderer.pos(box.minX, box.maxY, box.maxZ).color(red, green, blue, alpha).endVertex();
        worldRenderer.pos(box.minX, box.maxY, box.minZ).color(red, green, blue, alpha).endVertex();

        tessellator.draw();

        worldRenderer.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);

        // Vertical edges
        worldRenderer.pos(box.minX, box.minY, box.minZ).color(red, green, blue, alpha).endVertex();
        worldRenderer.pos(box.minX, box.maxY, box.minZ).color(red, green, blue, alpha).endVertex();

        worldRenderer.pos(box.maxX, box.minY, box.minZ).color(red, green, blue, alpha).endVertex();
        worldRenderer.pos(box.maxX, box.maxY, box.minZ).color(red, green, blue, alpha).endVertex();

        worldRenderer.pos(box.maxX, box.minY, box.maxZ).color(red, green, blue, alpha).endVertex();
        worldRenderer.pos(box.maxX, box.maxY, box.maxZ).color(red, green, blue, alpha).endVertex();

        worldRenderer.pos(box.minX, box.minY, box.maxZ).color(red, green, blue, alpha).endVertex();
        worldRenderer.pos(box.minX, box.maxY, box.maxZ).color(red, green, blue, alpha).endVertex();

        tessellator.draw();

        GL11.glLineWidth(1.0f);
        GlStateManager.enableDepth();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }
}
