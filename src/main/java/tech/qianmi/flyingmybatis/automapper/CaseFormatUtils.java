package tech.qianmi.flyingmybatis.automapper;

/**
 * Case format utilities
 *
 * @author yanan.zhang
 * @since 2021/2/18
 */
final class CaseFormatUtils {

    private CaseFormatUtils() {
        // Instantiation is not allowed
    }

    /**
     * Field name to column name
     */
    public static String toColumnName(String fieldName) {
        StringBuilder sb = new StringBuilder();
        appendChar(sb, fieldName, 0);
        return sb.toString();
    }

    /**
     * Type name to table name
     */
    public static String toTableName(String typeName) {
        StringBuilder sb = new StringBuilder();
        // Append first char
        sb.append((char) (typeName.charAt(0) + 32));
        appendChar(sb, typeName, 1);
        return sb.toString();
    }

    public static void appendChar(StringBuilder sb, String name, int startIndex) {
        for (int i = startIndex; i < name.length(); i++) {
            char ch = name.charAt(i);
            if (ch > 64 && ch < 91) {
                sb.append('_').append((char) (name.charAt(i) + 32));
            } else {
                sb.append(ch);
            }
        }
    }
}
