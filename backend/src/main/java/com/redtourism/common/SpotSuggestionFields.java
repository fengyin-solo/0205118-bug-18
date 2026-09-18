package com.redtourism.common;

import com.redtourism.entity.ScenicSpot;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 景点信息更正字段定义：字段标签、格式校验、取值/写值统一入口。
 * 提交建议与审核应用共用，避免两端各写一套 switch 导致字段错位。
 */
public final class SpotSuggestionFields {

    /** 字段元信息：label 页面展示名，maxLength 非空时的最大长度 */
    public static final class FieldDef {
        final String label;
        final int maxLength;
        FieldDef(String label, int maxLength) {
            this.label = label;
            this.maxLength = maxLength;
        }
        public String getLabel() { return label; }
        public int getMaxLength() { return maxLength; }
    }

    /** 全部支持更正的字段（保持顺序，供前端下拉使用） */
    private static final Map<String, FieldDef> FIELDS = new LinkedHashMap<>();
    static {
        FIELDS.put("name", new FieldDef("景点名称", 100));
        FIELDS.put("description", new FieldDef("景点描述", 0));
        FIELDS.put("region", new FieldDef("地区", 50));
        FIELDS.put("theme", new FieldDef("内容分类", 50));
        FIELDS.put("location", new FieldDef("地址", 255));
        FIELDS.put("openTime", new FieldDef("开放时间", 100));
        FIELDS.put("ticketPrice", new FieldDef("门票价格", 0));
        FIELDS.put("coordinates", new FieldDef("地图坐标", 0));
        FIELDS.put("trafficInfo", new FieldDef("交通信息", 0));
        FIELDS.put("ticketReservation", new FieldDef("门票预约", 500));
        FIELDS.put("suggestedDuration", new FieldDef("建议停留", 100));
        FIELDS.put("itemsToBring", new FieldDef("随身物品提醒", 0));
    }

    private SpotSuggestionFields() {}

    public static boolean isSupported(String fieldName) {
        return fieldName != null && FIELDS.containsKey(fieldName);
    }

    public static String label(String fieldName) {
        FieldDef def = FIELDS.get(fieldName);
        return def != null ? def.label : fieldName;
    }

    /**
     * 校验更正内容，合法返回 null，不合法返回具体原因（指明哪一项）。
     * 空内容不在此处拦截——应用时空值保留原值。
     */
    public static String validate(String fieldName, String newValue) {
        FieldDef def = FIELDS.get(fieldName);
        if (def == null) {
            return "不支持的更正字段：" + fieldName;
        }
        if (newValue == null || newValue.trim().isEmpty()) {
            return null; // 空值允许提交，应用时保留原值
        }
        String value = newValue.trim();
        if (def.maxLength > 0 && value.length() > def.maxLength) {
            return def.label + "长度不能超过 " + def.maxLength + " 个字符";
        }
        switch (fieldName) {
            case "ticketPrice": {
                BigDecimal price;
                try {
                    price = new BigDecimal(value);
                } catch (NumberFormatException e) {
                    return "门票价格必须是数字（单位：元），当前值“" + value + "”不是有效价格";
                }
                if (price.signum() < 0) {
                    return "门票价格不能为负数";
                }
                if (price.compareTo(new BigDecimal("99999999")) > 0) {
                    return "门票价格超出允许范围";
                }
                return null;
            }
            case "coordinates": {
                String[] parts = value.split("[,，]");
                if (parts.length != 2) {
                    return "地图坐标格式应为“经度,纬度”（英文逗号分隔），例如：106.71,27.72";
                }
                double lng;
                double lat;
                try {
                    lng = Double.parseDouble(parts[0].trim());
                    lat = Double.parseDouble(parts[1].trim());
                } catch (NumberFormatException e) {
                    return "地图坐标的经纬度必须是数字，当前值“" + value + "”无法识别";
                }
                if (lng < -180 || lng > 180 || lat < -90 || lat > 90) {
                    return "地图坐标超出有效范围（经度 -180~180，纬度 -90~90）";
                }
                return null;
            }
            default:
                return null;
        }
    }

    /** 读取景点当前字段值，统一转成字符串用于快照/展示 */
    public static String readValue(ScenicSpot spot, String fieldName) {
        if (spot == null || fieldName == null) return "";
        switch (fieldName) {
            case "name": return nullToEmpty(spot.getName());
            case "description": return nullToEmpty(spot.getDescription());
            case "region": return nullToEmpty(spot.getRegion());
            case "theme": return nullToEmpty(spot.getTheme());
            case "location": return nullToEmpty(spot.getLocation());
            case "openTime": return nullToEmpty(spot.getOpenTime());
            case "ticketPrice": return spot.getTicketPrice() != null ? spot.getTicketPrice().toPlainString() : "";
            case "coordinates":
                return spot.getLongitude() != null && spot.getLatitude() != null
                        ? spot.getLongitude() + "," + spot.getLatitude() : "";
            case "trafficInfo": return nullToEmpty(spot.getTrafficInfo());
            case "ticketReservation": return nullToEmpty(spot.getTicketReservation());
            case "suggestedDuration": return nullToEmpty(spot.getSuggestedDuration());
            case "itemsToBring": return nullToEmpty(spot.getItemsToBring());
            default: return "";
        }
    }

    /**
     * 将更正值写入景点字段。空值不写入（保留原值）。
     * 调用前应先通过 validate 校验。
     * @return true 表示实际修改了字段；false 表示因空值跳过（保留原值）
     */
    public static boolean applyValue(ScenicSpot spot, String fieldName, String newValue) {
        if (newValue == null || newValue.trim().isEmpty()) {
            return false;
        }
        String value = newValue.trim();
        switch (fieldName) {
            case "name": spot.setName(value); return true;
            case "description": spot.setDescription(value); return true;
            case "region": spot.setRegion(value); return true;
            case "theme": spot.setTheme(value); return true;
            case "location": spot.setLocation(value); return true;
            case "openTime": spot.setOpenTime(value); return true;
            case "ticketPrice": spot.setTicketPrice(new BigDecimal(value)); return true;
            case "coordinates": {
                String[] parts = value.split("[,，]");
                spot.setLongitude(Double.parseDouble(parts[0].trim()));
                spot.setLatitude(Double.parseDouble(parts[1].trim()));
                return true;
            }
            case "trafficInfo": spot.setTrafficInfo(value); return true;
            case "ticketReservation": spot.setTicketReservation(value); return true;
            case "suggestedDuration": spot.setSuggestedDuration(value); return true;
            case "itemsToBring": spot.setItemsToBring(value); return true;
            default:
                throw new IllegalArgumentException("不支持的更正字段：" + fieldName);
        }
    }

    private static String nullToEmpty(String s) {
        return s != null ? s : "";
    }
}
