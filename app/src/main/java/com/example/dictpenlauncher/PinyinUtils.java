package com.example.dictpenlauncher;

import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.exception.BadHanyuPinyinOutputFormatCombination;

/**
 * 拼音工具类 - 使用 tinypinyin 库实现汉字转拼音
 *
 * 排序规则：
 *   '#' 组（数字/特殊符号开头）排最前
 *   然后 A → Z
 *   同一字母块内：纯英文名称排在中文名称前面
 */
public class PinyinUtils {



    /**
     * 获取应用名的分组首字母
     */
    public static char getFirstLetter(String name) {
        if (name == null || name.isEmpty()) return '#';
        char first = name.charAt(0);

        if (isLatinLetter(first)) {
            return Character.toUpperCase(first);
        }

        if (isChinese(first)) {
            // 使用 pinyin4j 获取拼音首字母
            char p = getPinyinFirstLetter(first);
            if (p >= 'A' && p <= 'Z') return p;
        }

        return '#';
    }

    /**
     * 生成排序键
     */
    public static String getSortKey(String name) {
        if (name == null || name.isEmpty()) return "0#0";
        char first = name.charAt(0);

        if (isLatinLetter(first)) {
            return "1" + Character.toUpperCase(first) + "0" + name.toLowerCase();
        }

        if (isChinese(first)) {
            // 使用 pinyin4j 获取拼音首字母
            char p = getPinyinFirstLetter(first);
            if (p >= 'A' && p <= 'Z') {
                return "1" + p + "1" + name;
            }
        }

        return "0#0" + name.toLowerCase();
    }

    /**
     * 获取完整拼音字符串（用于调试或其他用途）
     * @param text 要转换的中文文本
     * @return 拼音字符串，每个汉字之间用空格分隔
     */
    public static String getPinyin(String text) {
        if (text == null || text.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        HanyuPinyinOutputFormat format = new HanyuPinyinOutputFormat();
        format.setCaseType(HanyuPinyinCaseType.UPPERCASE);
        format.setToneType(HanyuPinyinToneType.WITHOUT_TONE);
        
        for (char c : text.toCharArray()) {
            if (isChinese(c)) {
                try {
                    String[] pinyinArray = PinyinHelper.toHanyuPinyinStringArray(c, format);
                    if (pinyinArray != null && pinyinArray.length > 0) {
                        if (sb.length() > 0) sb.append(" ");
                        sb.append(pinyinArray[0]);
                    }
                } catch (BadHanyuPinyinOutputFormatCombination e) {
                    sb.append(c);
                }
            } else {
                if (sb.length() > 0) sb.append(" ");
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * 使用 pinyin4j 获取汉字拼音首字母
     */
    private static char getPinyinFirstLetter(char c) {
        if (!isChinese(c)) return '#';
        
        HanyuPinyinOutputFormat format = new HanyuPinyinOutputFormat();
        format.setCaseType(HanyuPinyinCaseType.UPPERCASE);
        format.setToneType(HanyuPinyinToneType.WITHOUT_TONE);
        
        try {
            String[] pinyinArray = PinyinHelper.toHanyuPinyinStringArray(c, format);
            if (pinyinArray != null && pinyinArray.length > 0) {
                String pinyin = pinyinArray[0];
                if (!pinyin.isEmpty()) {
                    return pinyin.charAt(0);
                }
            }
        } catch (BadHanyuPinyinOutputFormatCombination e) {
            // ignore
        }
        return '#';
    }

    private static boolean isLatinLetter(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
    }

    private static boolean isChinese(char c) {
        // CJK Unified Ideographs (中日韩统一表意文字)
        return c >= 0x4E00 && c <= 0x9FA5;
    }
}
