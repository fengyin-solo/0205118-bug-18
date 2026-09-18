package com.redtourism.common;

import com.redtourism.entity.ScenicSpot;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 景点信息更正建议支持的字段元数据。
 * 统一维护字段中文名、当前值读取、格式校验与字段应用逻辑，
 * 供提交建议、审核通过、审核列表展示共用，
 * 避免多处 switch 各自维护导致字段不生效或列表显示错位。
 */
public class SpotSuggestionFields {

    /** 字段名 -> 中文标签（有序） */
    private static final Map<String, String> LABELS = new LinkedHashMap<>();
    /** 字段最大长度（与 scenic_spot 列长度一致，0 表示不限制） */
    private static final Map<String, Integer> MAX_LENGTH = new LinkedHashMap<>();

    static {
        LABELS.put("name", "景点名称");              MAX_LENGTH.put("name", 100);
        LABELS.put("description", "景点描述");        MAX_LENGTH.put("description", 0);
        LABELS.put("location", "地址");              MAX_LENGTH.put("location", 255);
        LABELS.put("region", "所属地区");            MAX_LENGTH.put("region", 50);
        LABELS.put("theme", "内容分类");             MAX_LENGTH.put("theme", 50);
        LABELS.put("openTime", "开放时间");          MAX_LENGTH.put("openTime", 100);
        LABELS.put("ticketPrice", "门票价格");        MAX_LENGTH.put("ticketPrice", 0);
        LABELS.put("trafficInfo", "交通信息");        MAX_LENGTH.put("trafficInfo", 0);
        LABELS.put("longitude", "地图经度");          MAX_LENGTH.put("longitude", 0);
        LABELS.put("latitude", "地图纬度");           MAX_LENGTH.put("latitude", 0);
        LABELS.put("ticketReservation", "门票预约");  MAX_LENGTH.put("ticketReservation", 500);
        LABELS.put("suggestedDuration", "建议停留");  MAX_LENGTH.put("suggestedDuration", 100);
        LABELS.put("itemsToBring", "随身物品提醒");   MAX_LENGTH.put("itemsToBring", 0);
    }

    public static boolean isSupported(String fieldName) {
        return fieldName != null && LABELS.containsKey(fieldName);
    }

    public static String labelOf(String fieldName) {
        if (fieldName == null) return "";
        String label = LABELS.get(fieldName);
        return label != null ? label : fieldName;
    }

    /**
     * 读取景点当前字段值（用于展示“旧值”），
     * 保证审核处理列表与景点详情页看到的都是应用之后的最新值。
     */
    public static String currentValue(ScenicSpot spot, String fieldName) {
        if (spot == null || fieldName == null) return "";
        switch (fieldName) {
            case "name": return nullToEmpty(spot.getName());
            case "description": return nullToEmpty(spot.getDescription());
            case "location": return nullToEmpty(spot.getLocation());
            case "region": return nullToEmpty(spot.getRegion());
            case "theme": return nullToEmpty(spot.getTheme());
            case "openTime": return nullToEmpty(spot.getOpenTime());
            case "ticketPrice": return spot.getTicketPrice() != null ? spot.getTicketPrice().toString() : "";
            case "trafficInfo": return nullToEmpty(spot.getTrafficInfo());
            case "longitude": return spot.getLongitude() != null ? spot.getLongitude().toString() : "";
            case "latitude": return spot.getLatitude() != null ? spot.getLatitude().toString() : "";
            case "ticketReservation": return nullToEmpty(spot.getTicketReservation());
            case "suggestedDuration": return nullToEmpty(spot.getSuggestedDuration());
            case "itemsToBring": return nullToEmpty(spot.getItemsToBring());
            default: return "";
        }
    }

    /**
     * 校验更正内容是否合法。
     *
     * @return 不合法时返回指明具体哪一项无效的提示信息，合法返回 null
     */
    public static String validate(String fieldName, String newValue) {
        if (!isSupported(fieldName)) {
            return "不支持的更正字段【" + (fieldName == null ? "" : fieldName) + "】，无法应用";
        }
        String label = labelOf(fieldName);
        if (newValue == null || newValue.trim().isEmpty()) {
            return "【" + label + "】的更正内容为空，已保留原值不予通过；请驳回该建议或等待用户重新提交";
        }
        String v = newValue.trim();
        Integer max = MAX_LENGTH.get(fieldName);
        if (max != null && max > 0 && v.length() > max) {
            return "【" + label + "】内容超长（最多 " + max + " 个字符），无法应用";
        }
        switch (fieldName) {
            case "ticketPrice":
                try {
                    BigDecimal price = new BigDecimal(v);
                    if (price.compareTo(BigDecimal.ZERO) < 0) {
                        return "【" + label + "】格式无效：门票价格不能为负数";
                    }
                    if (price.stripTrailingZeros().scale() > 2) {
                        return "【" + label + "】格式无效：门票价格最多支持两位小数";
                    }
                    if (price.compareTo(new BigDecimal("99999999.99")) > 0) {
                        return "【" + label + "】格式无效：门票价格超出允许范围";
                    }
                } catch (NumberFormatException e) {
                    return "【" + label + "】格式无效：需为数字（如 25 或 25.50），当前内容「" + v + "」无法应用";
                }
                break;
            case "longitude":
                if (!isNumberInRange(v, -180, 180)) {
                    return "【" + label + "】格式无效：经度需为 -180 到 180 之间的数字，当前内容「" + v + "」无法应用";
                }
                break;
            case "latitude":
                if (!isNumberInRange(v, -90, 90)) {
                    return "【" + label + "】格式无效：纬度需为 -90 到 90 之间的数字，当前内容「" + v + "」无法应用";
                }
                break;
            default:
                break;
        }
        return null;
    }

    /**
     * 将更正内容应用到景点对象（调用前须先通过 validate 校验）。
     */
    public static void apply(ScenicSpot spot, String fieldName, String newValue) {
        if (spot == null || newValue == null) return;
        String v = newValue.trim();
        switch (fieldName) {
            case "name": spot.setName(v); break;
            case "description": spot.setDescription(v); break;
            case "location": spot.setLocation(v); break;
            case "region": spot.setRegion(v); break;
            case "theme": spot.setTheme(v); break;
            case "openTime": spot.setOpenTime(v); break;
            case "ticketPrice": spot.setTicketPrice(new BigDecimal(v)); break;
            case "trafficInfo": spot.setTrafficInfo(v); break;
            case "longitude": spot.setLongitude(Double.parseDouble(v)); break;
            case "latitude": spot.setLatitude(Double.parseDouble(v)); break;
            case "ticketReservation": spot.setTicketReservation(v); break;
            case "suggestedDuration": spot.setSuggestedDuration(v); break;
            case "itemsToBring": spot.setItemsToBring(v); break;
            default: break;
        }
    }

    private static boolean isNumberInRange(String v, double min, double max) {
        try {
            double d = Double.parseDouble(v);
            return !Double.isNaN(d) && !Double.isInfinite(d) && d >= min && d <= max;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
