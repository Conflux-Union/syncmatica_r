package cn.net.rms.syncmatica_r.material;

import cn.net.rms.syncmatica_r.ServerPlacement;
import cn.net.rms.syncmatica_r.util.SyncmaticaUtil;
import fi.dy.masa.malilib.util.StringUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
//#if MC < 12001
import net.minecraft.text.LiteralText;
//#endif
//#if MC >= 12001
//$$ import net.minecraft.text.Text;
//#endif
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public final class MaterialExportService {

    private static final Logger LOGGER = LogManager.getLogger(MaterialExportService.class);
    private static final DateTimeFormatter FILE_NAME_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private MaterialExportService() {
    }

    public static void exportPlacementToXlsx(final ServerPlacement placement) {
        if (placement == null || placement.getMaterialList() == null) {
            notifyClient("syncmatica_r.gui.label.material.export.error_no_data");
            return;
        }
        final List<SyncmaticaMaterialEntry> entries = new ArrayList<>(placement.getMaterialList().getEntries());
        entries.sort((left, right) -> {
            final int lm = left.getAmountMissing();
            final int rm = right.getAmountMissing();
            if (lm != rm) {
                return Integer.compare(rm, lm);
            }
            final String leftKey = left.getKey() == null ? "" : left.getKey().toString();
            final String rightKey = right.getKey() == null ? "" : right.getKey().toString();
            return leftKey.compareTo(rightKey);
        });
        if (entries.isEmpty()) {
            notifyClient("syncmatica_r.gui.label.material.export.error_no_data");
            return;
        }
        final MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.runDirectory == null) {
            LOGGER.warn("Skipping material export – client run directory unavailable");
            notifyClient("syncmatica_r.gui.label.material.export.error_generic");
            return;
        }
        final Path exportDir = client.runDirectory.toPath().resolve("syncmatica").resolve("exports");
        try {
            Files.createDirectories(exportDir);
        } catch (final IOException e) {
            LOGGER.error("Failed to create Syncmatica_r export directory {}", exportDir, e);
            notifyClient("syncmatica_r.gui.label.material.export.error_generic");
            return;
        }
        final String rawName = placement.getName() == null ? "placement" : placement.getName();
        final String sanitizedName = SyncmaticaUtil.sanitizeFileName(rawName);
        final String timestamp = FILE_NAME_TIME.format(LocalDateTime.now());
        final String fileName = "materials-" + sanitizedName + "-" + timestamp + ".xlsx";
        final Path file = exportDir.resolve(fileName);
        try (XSSFWorkbook workbook = new XSSFWorkbook(); OutputStream out = Files.newOutputStream(file)) {
            writeWorkbook(workbook, entries);
            workbook.write(out);
        } catch (final IOException e) {
            LOGGER.error("Failed to write Syncmatica_r material export {}", file, e);
            notifyClient("syncmatica_r.gui.label.material.export.error_generic");
            return;
        }
        notifyClient("syncmatica_r.gui.label.material.export.success", file.toAbsolutePath().toString());
    }

    private static void writeWorkbook(final XSSFWorkbook workbook, final List<SyncmaticaMaterialEntry> entries) {
        final Sheet sheet = workbook.createSheet("Materials");
        final XSSFCellStyle headerStyle = workbook.createCellStyle();
        final XSSFFont headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerFont.setColor(IndexedColors.WHITE.getIndex());
        headerStyle.setFont(headerFont);
        headerStyle.setFillForegroundColor(IndexedColors.GREY_80_PERCENT.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        final XSSFCellStyle evenRowStyle = workbook.createCellStyle();
        final XSSFCellStyle oddRowStyle = workbook.createCellStyle();
        evenRowStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        evenRowStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        oddRowStyle.setFillForegroundColor(IndexedColors.WHITE.getIndex());
        oddRowStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        final XSSFCellStyle missingFinishedStyle = workbook.createCellStyle();
        copyFont(workbook, missingFinishedStyle);
        missingFinishedStyle.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
        missingFinishedStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        final XSSFCellStyle missingUnfinishedStyle = workbook.createCellStyle();
        copyFont(workbook, missingUnfinishedStyle);
        missingUnfinishedStyle.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
        missingUnfinishedStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        int rowIndex = 0;
        final Row headerRow = sheet.createRow(rowIndex++);
        writeHeaderCell(headerRow, 0, "syncmatica_r.gui.label.material.column.material", headerStyle);
        writeHeaderCell(headerRow, 1, "syncmatica_r.gui.label.material.column.required", headerStyle);
        writeHeaderCell(headerRow, 2, "syncmatica_r.gui.label.material.column.stock", headerStyle);
        writeHeaderCell(headerRow, 3, "syncmatica_r.gui.label.material.column.missing", headerStyle);
        writeHeaderCell(headerRow, 4, "syncmatica_r.gui.label.material.column.claimed", headerStyle);

        for (final SyncmaticaMaterialEntry entry : entries) {
            final Row row = sheet.createRow(rowIndex);
            final boolean even = (rowIndex % 2) == 0;
            final CellStyle base = even ? evenRowStyle : oddRowStyle;
            writeDataRow(row, entry, base, missingFinishedStyle, missingUnfinishedStyle);
            rowIndex++;
        }
        for (int c = 0; c < 5; c++) {
            sheet.autoSizeColumn(c);
        }
    }

    private static void writeHeaderCell(final Row row, final int column, final String key, final CellStyle style) {
        final Cell cell = row.createCell(column);
        cell.setCellValue(StringUtils.translate(key));
        cell.setCellStyle(style);
    }

    private static void writeDataRow(final Row row,
                                     final SyncmaticaMaterialEntry entry,
                                     final CellStyle baseStyle,
                                     final CellStyle finishedStyle,
                                     final CellStyle unfinishedStyle) {
        final boolean finished = entry.isFinished();
        final java.util.List<String> claimers = entry.getClaimers() == null
                ? java.util.Collections.emptyList()
                : entry.getClaimers();
        final boolean claimed = !claimers.isEmpty();

        Cell cell = row.createCell(0);
        cell.setCellValue(resolveDisplayName(entry));
        cell.setCellStyle(baseStyle);

        cell = row.createCell(1);
        cell.setCellValue(Math.max(0, entry.getAmountRequired()));
        cell.setCellStyle(baseStyle);

        cell = row.createCell(2);
        cell.setCellValue(Math.max(0, entry.getStockingSupplied()));
        cell.setCellStyle(baseStyle);

        final int missing = Math.max(0, entry.getAmountMissing());
        cell = row.createCell(3);
        cell.setCellValue(missing);
        final CellStyle missingStyle;
        if (finished) {
            missingStyle = finishedStyle;
        } else if (claimed) {
            missingStyle = unfinishedStyle;
        } else {
            missingStyle = baseStyle;
        }
        cell.setCellStyle(missingStyle);

        cell = row.createCell(4);
        cell.setCellValue(String.join(", ", claimers));
        cell.setCellStyle(baseStyle);
    }

    private static void copyFont(final XSSFWorkbook workbook, final XSSFCellStyle style) {
        final Font font = workbook.createFont();
        font.setColor(IndexedColors.BLACK.getIndex());
        style.setFont(font);
    }

    private static String resolveDisplayName(final SyncmaticaMaterialEntry entry) {
        if (entry == null || entry.getKey() == null) {
            return "unknown";
        }
        final ItemStack stack = resolveDisplayStack(entry.getKey());
        if (!stack.isEmpty()) {
            return stack.getName().getString();
        }
        return entry.getKey().toString();
    }

    private static ItemStack resolveDisplayStack(final MaterialKey key) {
        if (key == null) {
            return ItemStack.EMPTY;
        }
        //#if MC >= 260100
        //$$ final Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(key.itemId());
        //#else
        final Item item = net.minecraft.util.registry.Registry.ITEM.get(key.itemId());
        //#endif
        if (item == Items.AIR) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(item);
    }

    private static void notifyClient(final String key, final Object... args) {
        final MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.inGameHud == null) {
            return;
        }
        final InGameHud hud = client.inGameHud;
        final ChatHud chat = hud.getChatHud();
        if (chat == null) {
            return;
        }
        final String message = StringUtils.translate(key, args);
        //#if MC >= 12001
        //#if MC >= 260100
        //$$ chat.addClientSystemMessage(Component.literal(message));
        //#else
        //$$ chat.addMessage(Text.literal(message));
        //#endif
        //#else
        chat.addMessage(new LiteralText(message));
        //#endif
    }
}
