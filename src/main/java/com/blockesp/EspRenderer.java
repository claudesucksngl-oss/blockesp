package com.blockesp;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Draws the outlines and tracers. Lines are built out of thin quads and drawn with a custom
 * pipeline that has the depth test turned off, so they show through walls.
 * Structure follows the Fabric 1.21.11 "Rendering in the World" guide.
 */
public final class EspRenderer {
    private static final RenderPipeline PIPELINE = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("blockesp", "pipeline/esp_through_walls"))
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .build()
    );

    private static final ByteBufferBuilder ALLOCATOR = new ByteBufferBuilder(RenderType.SMALL_BUFFER_SIZE);
    private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
    private static final Vector3f MODEL_OFFSET = new Vector3f();
    private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();
    private static MappableRingBuffer vertexBuffer;

    private EspRenderer() {}

    public static void init() {
        WorldRenderEvents.BEFORE_TRANSLUCENT.register(EspRenderer::onRender);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> close());
    }

    private static void onRender(WorldRenderContext context) {
        if (!Config.master || (!Config.outline && !Config.tracers)) return;
        List<Scanner.Hit> hits = Scanner.hits;
        if (hits.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        PoseStack matrices = context.matrices();
        Vec3 cam = context.worldState().cameraRenderState.pos;
        Vec3 look = mc.player.getViewVector(1.0F);
        Vec3 tracerStart = look.scale(0.6);

        Matrix4fc m = matrices.last().pose();
        BufferBuilder buffer = new BufferBuilder(ALLOCATOR, PIPELINE.getVertexFormatMode(), PIPELINE.getVertexFormat());

        for (Scanner.Hit h : hits) {
            float r = ((h.color() >> 16) & 0xFF) / 255f;
            float g = ((h.color() >> 8) & 0xFF) / 255f;
            float b = (h.color() & 0xFF) / 255f;

            // Everything below is camera-relative.
            double rx = h.x() - cam.x, ry = h.y() - cam.y, rz = h.z() - cam.z;
            Vec3 center = new Vec3(rx + 0.5, ry + 0.5, rz + 0.5);
            double dist = center.length();

            if (Config.outline) {
                float t = (float) Math.max(0.02, 0.0012 * dist); // keeps the lines visible far away
                float x0 = (float) rx, y0 = (float) ry, z0 = (float) rz;
                float x1 = x0 + 1f, y1 = y0 + 1f, z1 = z0 + 1f;
                // 4 edges along X
                box(m, buffer, x0 - t, y0 - t, z0 - t, x1 + t, y0 + t, z0 + t, r, g, b);
                box(m, buffer, x0 - t, y1 - t, z0 - t, x1 + t, y1 + t, z0 + t, r, g, b);
                box(m, buffer, x0 - t, y0 - t, z1 - t, x1 + t, y0 + t, z1 + t, r, g, b);
                box(m, buffer, x0 - t, y1 - t, z1 - t, x1 + t, y1 + t, z1 + t, r, g, b);
                // 4 edges along Y
                box(m, buffer, x0 - t, y0 - t, z0 - t, x0 + t, y1 + t, z0 + t, r, g, b);
                box(m, buffer, x1 - t, y0 - t, z0 - t, x1 + t, y1 + t, z0 + t, r, g, b);
                box(m, buffer, x0 - t, y0 - t, z1 - t, x0 + t, y1 + t, z1 + t, r, g, b);
                box(m, buffer, x1 - t, y0 - t, z1 - t, x1 + t, y1 + t, z1 + t, r, g, b);
                // 4 edges along Z
                box(m, buffer, x0 - t, y0 - t, z0 - t, x0 + t, y0 + t, z1 + t, r, g, b);
                box(m, buffer, x1 - t, y0 - t, z0 - t, x1 + t, y0 + t, z1 + t, r, g, b);
                box(m, buffer, x0 - t, y1 - t, z0 - t, x0 + t, y1 + t, z1 + t, r, g, b);
                box(m, buffer, x1 - t, y1 - t, z0 - t, x1 + t, y1 + t, z1 + t, r, g, b);
            }

            if (Config.tracers) {
                tracer(m, buffer, tracerStart, center, look, r, g, b);
            }
        }

        MeshData mesh = buffer.build();
        if (mesh == null) return; // nothing was added

        MeshData.DrawState drawState = mesh.drawState();
        VertexFormat format = drawState.format();
        GpuBuffer vertices = upload(drawState, format, mesh);
        draw(mc, mesh, drawState, vertices, format);
        vertexBuffer.rotate();
    }

    /** A flat ribbon from s to e that always faces the camera. Written twice so culling can't hide it. */
    private static void tracer(Matrix4fc m, BufferBuilder buf, Vec3 s, Vec3 e, Vec3 look,
                               float r, float g, float b) {
        Vec3 d = e.subtract(s);
        Vec3 side = d.cross(look);
        if (side.lengthSqr() < 1.0E-8) return; // block is dead ahead, nothing to draw
        side = side.normalize();

        double wStart = 0.0014 * s.length();
        double wEnd = 0.0014 * e.length();
        Vec3 a = s.subtract(side.scale(wStart));
        Vec3 bb = s.add(side.scale(wStart));
        Vec3 c = e.add(side.scale(wEnd));
        Vec3 dd = e.subtract(side.scale(wEnd));

        vertex(m, buf, a, r, g, b);
        vertex(m, buf, bb, r, g, b);
        vertex(m, buf, c, r, g, b);
        vertex(m, buf, dd, r, g, b);

        vertex(m, buf, dd, r, g, b);
        vertex(m, buf, c, r, g, b);
        vertex(m, buf, bb, r, g, b);
        vertex(m, buf, a, r, g, b);
    }

    private static void vertex(Matrix4fc m, BufferBuilder buf, Vec3 p, float r, float g, float b) {
        buf.addVertex(m, (float) p.x, (float) p.y, (float) p.z).setColor(r, g, b, 1f);
    }

    private static void box(Matrix4fc m, BufferBuilder buffer,
                            float minX, float minY, float minZ, float maxX, float maxY, float maxZ,
                            float red, float green, float blue) {
        float alpha = 1f;
        // Front Face
        buffer.addVertex(m, minX, minY, maxZ).setColor(red, green, blue, alpha);
        buffer.addVertex(m, maxX, minY, maxZ).setColor(red, green, blue, alpha);
        buffer.addVertex(m, maxX, maxY, maxZ).setColor(red, green, blue, alpha);
        buffer.addVertex(m, minX, maxY, maxZ).setColor(red, green, blue, alpha);

        // Back face
        buffer.addVertex(m, maxX, minY, minZ).setColor(red, green, blue, alpha);
        buffer.addVertex(m, minX, minY, minZ).setColor(red, green, blue, alpha);
        buffer.addVertex(m, minX, maxY, minZ).setColor(red, green, blue, alpha);
        buffer.addVertex(m, maxX, maxY, minZ).setColor(red, green, blue, alpha);

        // Left face
        buffer.addVertex(m, minX, minY, minZ).setColor(red, green, blue, alpha);
        buffer.addVertex(m, minX, minY, maxZ).setColor(red, green, blue, alpha);
        buffer.addVertex(m, minX, maxY, maxZ).setColor(red, green, blue, alpha);
        buffer.addVertex(m, minX, maxY, minZ).setColor(red, green, blue, alpha);

        // Right face
        buffer.addVertex(m, maxX, minY, maxZ).setColor(red, green, blue, alpha);
        buffer.addVertex(m, maxX, minY, minZ).setColor(red, green, blue, alpha);
        buffer.addVertex(m, maxX, maxY, minZ).setColor(red, green, blue, alpha);
        buffer.addVertex(m, maxX, maxY, maxZ).setColor(red, green, blue, alpha);

        // Top face
        buffer.addVertex(m, minX, maxY, maxZ).setColor(red, green, blue, alpha);
        buffer.addVertex(m, maxX, maxY, maxZ).setColor(red, green, blue, alpha);
        buffer.addVertex(m, maxX, maxY, minZ).setColor(red, green, blue, alpha);
        buffer.addVertex(m, minX, maxY, minZ).setColor(red, green, blue, alpha);

        // Bottom face
        buffer.addVertex(m, minX, minY, minZ).setColor(red, green, blue, alpha);
        buffer.addVertex(m, maxX, minY, minZ).setColor(red, green, blue, alpha);
        buffer.addVertex(m, maxX, minY, maxZ).setColor(red, green, blue, alpha);
        buffer.addVertex(m, minX, minY, maxZ).setColor(red, green, blue, alpha);
    }

    private static GpuBuffer upload(MeshData.DrawState drawState, VertexFormat format, MeshData mesh) {
        int vertexBufferSize = drawState.vertexCount() * format.getVertexSize();

        if (vertexBuffer == null || vertexBuffer.size() < vertexBufferSize) {
            if (vertexBuffer != null) {
                vertexBuffer.close();
            }
            vertexBuffer = new MappableRingBuffer(() -> "blockesp vertex buffer",
                GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_MAP_WRITE, vertexBufferSize);
        }

        CommandEncoder commandEncoder = RenderSystem.getDevice().createCommandEncoder();
        try (GpuBuffer.MappedView mappedView = commandEncoder.mapBuffer(
            vertexBuffer.currentBuffer().slice(0, mesh.vertexBuffer().remaining()), false, true)) {
            MemoryUtil.memCopy(mesh.vertexBuffer(), mappedView.data());
        }
        return vertexBuffer.currentBuffer();
    }

    private static void draw(Minecraft client, MeshData mesh, MeshData.DrawState drawState,
                             GpuBuffer vertices, VertexFormat format) {
        GpuBuffer indices;
        VertexFormat.IndexType indexType;

        if (PIPELINE.getVertexFormatMode() == VertexFormat.Mode.QUADS) {
            mesh.sortQuads(ALLOCATOR, RenderSystem.getProjectionType().vertexSorting());
            indices = PIPELINE.getVertexFormat().uploadImmediateIndexBuffer(mesh.indexBuffer());
            indexType = mesh.drawState().indexType();
        } else {
            RenderSystem.AutoStorageIndexBuffer shapeIndexBuffer =
                RenderSystem.getSequentialBuffer(PIPELINE.getVertexFormatMode());
            indices = shapeIndexBuffer.getBuffer(drawState.indexCount());
            indexType = shapeIndexBuffer.type();
        }

        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
            .writeTransform(RenderSystem.getModelViewMatrix(), COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX);

        try (RenderPass renderPass = RenderSystem.getDevice()
            .createCommandEncoder()
            .createRenderPass(() -> "blockesp render",
                client.getMainRenderTarget().getColorTextureView(), OptionalInt.empty(),
                client.getMainRenderTarget().getDepthTextureView(), OptionalDouble.empty())) {
            renderPass.setPipeline(PIPELINE);

            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("DynamicTransforms", dynamicTransforms);

            renderPass.setVertexBuffer(0, vertices);
            renderPass.setIndexBuffer(indices, indexType);
            renderPass.drawIndexed(0, 0, drawState.indexCount(), 1);
        }

        mesh.close();
    }

    public static void close() {
        ALLOCATOR.close();
        if (vertexBuffer != null) {
            vertexBuffer.close();
            vertexBuffer = null;
        }
    }
}
