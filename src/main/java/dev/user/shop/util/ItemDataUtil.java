package dev.user.shop.util;

import de.tr7zw.nbtapi.NBT;
import de.tr7zw.nbtapi.iface.ReadWriteNBT;
import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4Decompressor;
import net.jpountz.lz4.LZ4Factory;
import net.jpountz.xxhash.XXHashFactory;
import org.bukkit.inventory.ItemStack;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * 物品数据序列化/反序列化 + LZ4 压缩工具类
 * 用于全球商店的物品持久化存储
 */
public class ItemDataUtil {

    private static final LZ4Compressor compressor = LZ4Factory.fastestInstance().fastCompressor();
    private static final LZ4Decompressor decompressor = LZ4Factory.fastestInstance().fastDecompressor();

    /**
     * 序列化 ItemStack 为 LZ4 压缩的 byte[]
     * 流程: ItemStack → NBT Compound → String → UTF-8 bytes → LZ4 压缩
     * 格式: [4字节原始长度][LZ4压缩数据]
     */
    public static byte[] serializeItem(ItemStack itemStack) {
        if (itemStack == null) return null;

        // NBT 序列化
        ReadWriteNBT nbt = NBT.itemStackToNBT(itemStack);
        String nbtString = nbt.toString();
        byte[] rawBytes = nbtString.getBytes(StandardCharsets.UTF_8);

        // LZ4 压缩，带原始长度前缀
        int originalLength = rawBytes.length;
        int maxCompressedLength = compressor.maxCompressedLength(originalLength);
        ByteBuffer buffer = ByteBuffer.allocate(4 + maxCompressedLength);
        buffer.putInt(originalLength);
        int compressedLength = compressor.compress(rawBytes, 0, originalLength, buffer.array(), 4, maxCompressedLength);
        byte[] result = new byte[4 + compressedLength];
        System.arraycopy(buffer.array(), 0, result, 0, 4 + compressedLength);
        return result;
    }

    /**
     * 从 LZ4 压缩的 byte[] 反序列化为 ItemStack
     * 流程: byte[] → 解压 → UTF-8 String → NBT Compound → ItemStack
     * @return ItemStack，如果数据损坏则返回 null
     */
    public static ItemStack deserializeItem(byte[] data) {
        if (data == null || data.length < 4) return null;

        try {
            // 读取原始长度
            ByteBuffer header = ByteBuffer.wrap(data, 0, 4);
            int originalLength = header.getInt();
            if (originalLength <= 0 || originalLength > 10 * 1024 * 1024) return null; // 防御性检查

            // LZ4 解压
            byte[] decompressed = new byte[originalLength];
            decompressor.decompress(data, 4, decompressed, 0, originalLength);

            // NBT 反序列化
            String nbtString = new String(decompressed, StandardCharsets.UTF_8);
            ReadWriteNBT nbt = de.tr7zw.nbtapi.NBT.parseNBT(nbtString);
            return NBT.itemStackFromNBT(nbt);
        } catch (Exception e) {
            return null;
        }
    }
}
