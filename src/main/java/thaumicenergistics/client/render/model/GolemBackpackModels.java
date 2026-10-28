package thaumicenergistics.client.render.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import net.minecraft.client.renderer.FaceInfo;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.common.entities.BackpackSkins;

/**
 * Per-face model loader adapted from Thaumic Energistics: CE (Minecraft 26.1.2).
 * Copyright (c) 2026 Thaumic Energistics: CE contributors. MIT License.
 * Full notice: META-INF/licenses/thaumicenergistics-ce-backpack.txt.
 * Uses direct texture coordinates; these vertices must not use the block atlas.
 */
@net.neoforged.api.distmarker.OnlyIn(net.neoforged.api.distmarker.Dist.CLIENT)
public final class GolemBackpackModels {
    public static final ResourceLocation RESOURCE = ThaumicEnergistics.id("models/entity/golem_backpack.json");
    private static volatile GolemBackpackModels current = new GolemBackpackModels(
            List.of(), Map.of(), Map.of(), Set.of(), "", new Vector3f(), new Vector3f(), new Vector3f(1));

    private final List<Quad> quads;
    private final Map<String, ResourceLocation> textures;
    private final Map<String, String> connectedTextures;
    private final Set<ResourceLocation> emissiveTextures;
    private final String skinTexture;
    private final Vector3f translation;
    private final Vector3f rotation;
    private final Vector3f scale;
    private final Map<ResourceLocation, RenderType> renderTypes = new HashMap<>();

    private record Vertex(Vector3f position, float u, float v) {}
    private record Quad(String texture, List<Vertex> vertices, Vector3f normal) {}

    private GolemBackpackModels(List<Quad> quads, Map<String, ResourceLocation> textures,
            Map<String, String> connectedTextures, Set<ResourceLocation> emissiveTextures, String skinTexture,
            Vector3f translation, Vector3f rotation, Vector3f scale) {
        this.quads = List.copyOf(quads);
        this.textures = Map.copyOf(textures);
        this.connectedTextures = Map.copyOf(connectedTextures);
        this.emissiveTextures = Set.copyOf(emissiveTextures);
        this.skinTexture = skinTexture;
        this.translation = translation;
        this.rotation = rotation;
        this.scale = scale;
    }

    public static GolemBackpackModels current() { return current; }

    public static void reload(ResourceManager resources) {
        try (var reader = resources.getResourceOrThrow(RESOURCE).openAsReader()) {
            current = load(JsonParser.parseReader(reader).getAsJsonObject());
        } catch (Exception error) {
            ThaumicEnergistics.LOG.error("Failed to load backpack model {}. Keeping the previous model.", RESOURCE, error);
        }
    }

    public static GolemBackpackModels load(JsonObject data) {
        var attachment = data.getAsJsonObject("attachment");
        var origin = vector(attachment.getAsJsonArray("origin"));
        var textures = new HashMap<String, ResourceLocation>();
        data.getAsJsonObject("textures").entrySet().forEach(entry -> {
            var id = ResourceLocation.parse(entry.getValue().getAsString());
            String path = id.getPath();
            if (!path.startsWith("textures/")) path = "textures/" + path;
            if (!path.endsWith(".png")) path += ".png";
            textures.put(entry.getKey(), ResourceLocation.fromNamespaceAndPath(id.getNamespace(), path));
        });
        var connected = new HashMap<String, String>();
        data.getAsJsonObject("connected_textures").entrySet().forEach(entry ->
                connected.put(entry.getKey(), entry.getValue().getAsString()));
        String skinTexture = data.get("skin_texture").getAsString();
        if (!textures.containsKey(skinTexture)) throw new IllegalArgumentException("Unknown skin texture: " + skinTexture);
        connected.forEach((source, target) -> {
            if (!textures.containsKey(source) || !textures.containsKey(target)) {
                throw new IllegalArgumentException("Unknown connected texture: " + source + " -> " + target);
            }
        });
        var emissive = new HashSet<ResourceLocation>();
        if (data.has("emissive_textures")) {
            for (var value : data.getAsJsonArray("emissive_textures")) {
                String key = value.getAsString();
                if (!textures.containsKey(key)) throw new IllegalArgumentException("Unknown emissive texture: " + key);
                emissive.add(textures.get(key));
            }
        }
        var quads = new ArrayList<Quad>();
        for (var value : data.getAsJsonArray("elements")) {
            var element = value.getAsJsonObject();
            var from = vector(element.getAsJsonArray("from"));
            var to = vector(element.getAsJsonArray("to"));
            var transform = elementTransform(element);
            for (var entry : element.getAsJsonObject("faces").entrySet()) {
                var direction = Direction.byName(entry.getKey());
                if (direction == null) throw new IllegalArgumentException("Unknown face: " + entry.getKey());
                var face = entry.getValue().getAsJsonObject();
                String texture = face.get("texture").getAsString();
                if (!texture.startsWith("#") || !textures.containsKey(texture.substring(1))) {
                    throw new IllegalArgumentException("Unknown face texture: " + texture);
                }
                var uv = face.getAsJsonArray("uv");
                if (uv.size() != 4) throw new IllegalArgumentException("Expected four UV coordinates");
                float u0 = number(uv, 0) / 16, v0 = number(uv, 1) / 16;
                float u1 = number(uv, 2) / 16, v1 = number(uv, 3) / 16;
                float[][] corners = {{u0, v0}, {u0, v1}, {u1, v1}, {u1, v0}};
                int angle = face.has("rotation") ? face.get("rotation").getAsInt() : 0;
                if (angle % 90 != 0) throw new IllegalArgumentException("Face rotation must be a multiple of 90 degrees");
                var vertices = new ArrayList<Vertex>(4);
                for (int i = 0; i < 4; i++) {
                    var position = faceVertex(direction, i, from, to);
                    transform.transformPosition(position);
                    position.sub(origin).div(16);
                    var coordinate = corners[Math.floorMod(i + angle / 90, 4)];
                    vertices.add(new Vertex(position, coordinate[0], coordinate[1]));
                }
                var normal = new Vector3f(vertices.get(1).position()).sub(vertices.get(0).position())
                        .cross(new Vector3f(vertices.get(2).position()).sub(vertices.get(0).position()));
                if (normal.lengthSquared() == 0) continue;
                normal.normalize();
                quads.add(new Quad(texture.substring(1), List.copyOf(vertices), normal));
            }
        }
        if (quads.isEmpty()) throw new IllegalArgumentException("Backpack model has no visible faces");
        return new GolemBackpackModels(quads, textures, connected, emissive, skinTexture,
                vector(attachment.getAsJsonArray("translation")),
                vector(attachment.getAsJsonArray("rotation")), vector(attachment.getAsJsonArray("scale")));
    }

    // Minecraft 1.21.1 exposes the face vertex indices rather than select(from, to).
    private static Vector3f faceVertex(Direction direction, int index, Vector3f from, Vector3f to) {
        var vertex = FaceInfo.fromFacing(direction).getVertexInfo(index);
        float[] bounds = new float[6];
        bounds[FaceInfo.Constants.MIN_X] = from.x;
        bounds[FaceInfo.Constants.MIN_Y] = from.y;
        bounds[FaceInfo.Constants.MIN_Z] = from.z;
        bounds[FaceInfo.Constants.MAX_X] = to.x;
        bounds[FaceInfo.Constants.MAX_Y] = to.y;
        bounds[FaceInfo.Constants.MAX_Z] = to.z;
        return new Vector3f(bounds[vertex.xFace], bounds[vertex.yFace], bounds[vertex.zFace]);
    }

    private static Matrix4f elementTransform(JsonObject element) {
        var transform = new Matrix4f();
        if (!element.has("rotation")) return transform;
        var rotation = element.getAsJsonObject("rotation");
        var origin = vector(rotation.getAsJsonArray("origin"));
        float angle = (float) Math.toRadians(rotation.get("angle").getAsDouble());
        if (!Float.isFinite(angle)) throw new IllegalArgumentException("Non-finite element rotation");
        String axis = rotation.get("axis").getAsString();
        transform.translate(origin);
        switch (axis) {
            case "x" -> transform.rotateX(angle);
            case "y" -> transform.rotateY(angle);
            case "z" -> transform.rotateZ(angle);
            default -> throw new IllegalArgumentException("Unknown rotation axis: " + axis);
        }
        if (rotation.has("rescale") && rotation.get("rescale").getAsBoolean()) {
            float factor = 1 / (float) Math.cos(angle);
            if (!Float.isFinite(factor)) throw new IllegalArgumentException("Invalid rotation rescale");
            transform.scale(axis.equals("x") ? 1 : factor,
                    axis.equals("y") ? 1 : factor, axis.equals("z") ? 1 : factor);
        }
        return transform.translate(new Vector3f(origin).negate());
    }

    private static Vector3f vector(JsonArray data) {
        if (data.size() != 3) throw new IllegalArgumentException("Expected a three-dimensional vector");
        return new Vector3f(number(data, 0), number(data, 1), number(data, 2));
    }

    private static float number(JsonArray data, int index) {
        float number = data.get(index).getAsFloat();
        if (!Float.isFinite(number)) throw new IllegalArgumentException("Non-finite model coordinate");
        return number;
    }

    public void render(PoseStack poses, MultiBufferSource buffers, BackpackSkins skin,
            boolean working, int light, int overlay) {
        poses.pushPose();
        poses.translate(translation.x, translation.y, translation.z);
        poses.mulPose(new org.joml.Quaternionf().rotationXYZ(
                (float) Math.toRadians(rotation.x), (float) Math.toRadians(rotation.y), (float) Math.toRadians(rotation.z)));
        poses.scale(scale.x, scale.y, scale.z);
        var pose = poses.last();
        var normal = new Vector3f();
        for (var quad : quads) {
            String key = working ? connectedTextures.getOrDefault(quad.texture(), quad.texture()) : quad.texture();
            var texture = key.equals(skinTexture) ? skin.getTextureLocation() : textures.get(key);
            boolean emissive = emissiveTextures.contains(texture);
            VertexConsumer buffer = buffers.getBuffer(renderTypes.computeIfAbsent(texture,
                    id -> emissiveTextures.contains(id)
                            ? RenderType.entityTranslucentEmissive(id)
                            : RenderType.entityCutoutNoCull(id)));
            normal.set(quad.normal());
            pose.normal().transform(normal).normalize();
            int faceLight = emissive ? net.minecraft.client.renderer.LightTexture.FULL_BRIGHT : light;
            for (var vertex : quad.vertices()) {
                var position = vertex.position();
                buffer.addVertex(pose, position.x, position.y, position.z)
                        .setColor(255, 255, 255, 255).setUv(vertex.u(), vertex.v())
                        .setOverlay(overlay).setLight(faceLight).setNormal(normal.x, normal.y, normal.z);
            }
        }
        poses.popPose();
    }
}
