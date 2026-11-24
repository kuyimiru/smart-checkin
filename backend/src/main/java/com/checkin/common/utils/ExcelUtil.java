package com.checkin.common.utils;

import com.checkin.common.exception.BusinessException;
import com.checkin.entity.Roster;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Excel / CSV 工具。
 *
 * v1 只支持 Excel 且是死代码（没有任何调用方），清单 R-01 要求「支持 xlsx 和 csv」，
 * 这里补齐 CSV 分支并对内容做基本校验。
 */
public class ExcelUtil {

    /** 花名册列顺序：姓名 | 性别 | 电话 */
    private static final int COL_NAME = 0;
    private static final int COL_GENDER = 1;
    private static final int COL_PHONE = 2;

    /**
     * 解析花名册文件（自动识别 xlsx / xls / csv）。
     *
     * @throws BusinessException 文件为空、格式不支持、或没有解析出任何有效行
     */
    public static List<Roster> importRoster(MultipartFile file, Long activityId) throws IOException {
        if (file == null || file.isEmpty()) {
            throw BusinessException.badRequest("文件为空");
        }
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        List<Roster> list;
        if (filename.endsWith(".csv")) {
            list = parseCsv(file, activityId);
        } else if (filename.endsWith(".xlsx") || filename.endsWith(".xls")) {
            list = parseExcel(file, activityId);
        } else {
            throw BusinessException.badRequest("文件格式不支持，请上传 xlsx / xls / csv");
        }
        if (list.isEmpty()) {
            throw BusinessException.badRequest("未解析到有效数据，请检查文件内容（首行需为表头：姓名,性别,电话）");
        }
        return list;
    }

    private static List<Roster> parseExcel(MultipartFile file, Long activityId) throws IOException {
        List<Roster> list = new ArrayList<>();
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) { continue; }
                String name = getCellString(row.getCell(COL_NAME));
                if (name.isEmpty()) { continue; }
                Roster r = new Roster();
                r.setActivityId(activityId);
                r.setName(name);
                r.setGender(normalizeGender(getCellString(row.getCell(COL_GENDER))));
                r.setPhone(nullIfEmpty(getCellString(row.getCell(COL_PHONE))));
                list.add(r);
            }
        }
        return list;
    }

    private static List<Roster> parseCsv(MultipartFile file, Long activityId) throws IOException {
        List<Roster> list = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line = reader.readLine();
            if (line == null) { return list; }
            // 去掉 UTF-8 BOM，否则第一个表头会带不可见字符
            if (line.startsWith("\uFEFF")) { line = line.substring(1); }

            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) { continue; }
                String[] cells = splitCsvLine(line);
                String name = getCell(cells, COL_NAME);
                if (name.isEmpty()) { continue; }
                Roster r = new Roster();
                r.setActivityId(activityId);
                r.setName(name);
                r.setGender(normalizeGender(getCell(cells, COL_GENDER)));
                r.setPhone(nullIfEmpty(getCell(cells, COL_PHONE)));
                list.add(r);
            }
        }
        return list;
    }

    /** 简易 CSV 拆分，支持双引号包裹的字段 */
    private static String[] splitCsvLine(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cur.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString());
        return out.toArray(new String[0]);
    }

    private static String getCell(String[] cells, int idx) {
        if (cells == null || idx >= cells.length || cells[idx] == null) { return ""; }
        return cells[idx].replace("\"", "").trim();
    }

    /** 统一「男/女」写法，便于与 users.gender 精确匹配 */
    private static String normalizeGender(String raw) {
        if (raw == null) { return null; }
        String v = raw.trim();
        if (v.isEmpty()) { return null; }
        if (v.equals("男") || v.equalsIgnoreCase("m") || v.equalsIgnoreCase("male") || v.equals("1")) { return "男"; }
        if (v.equals("女") || v.equalsIgnoreCase("f") || v.equalsIgnoreCase("female") || v.equals("2")) { return "女"; }
        return v;
    }

    private static String nullIfEmpty(String s) {
        return s == null || s.trim().isEmpty() ? null : s.trim();
    }

    private static String getCellString(Cell cell) {
        if (cell == null) { return ""; }
        switch (cell.getCellType()) {
            case STRING: return cell.getStringCellValue().trim();
            case NUMERIC:
                double v = cell.getNumericCellValue();
                return v == Math.floor(v) && !Double.isInfinite(v) ? String.valueOf((long) v) : String.valueOf(v);
            case BOOLEAN: return String.valueOf(cell.getBooleanCellValue());
            default: return "";
        }
    }

    /** 导出 Excel（清单 E-01） */
    public static byte[] exportCheckInData(List<String[]> data, String[] headers) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("打卡数据");
            Row hr = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) { hr.createCell(i).setCellValue(headers[i]); }
            for (int i = 0; i < data.size(); i++) {
                Row row = sheet.createRow(i + 1);
                String[] rd = data.get(i);
                for (int j = 0; j < rd.length; j++) { row.createCell(j).setCellValue(rd[j] != null ? rd[j] : ""); }
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);
            return bos.toByteArray();
        }
    }
}
