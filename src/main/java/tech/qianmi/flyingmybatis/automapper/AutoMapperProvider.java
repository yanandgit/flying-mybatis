package tech.qianmi.flyingmybatis.automapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.builder.annotation.ProviderContext;
import org.apache.ibatis.builder.annotation.ProviderMethodResolver;
import org.apache.ibatis.jdbc.SQL;
import tech.qianmi.flyingmybatis.PrimaryKey.KeyType;

import java.util.Collection;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.util.Objects.nonNull;
import static java.util.Objects.requireNonNull;
import static java.util.UUID.randomUUID;
import static tech.qianmi.flyingmybatis.automapper.MetaDataCache.ENTITY_PLACEHOLDER;
import static tech.qianmi.flyingmybatis.automapper.MybatisHelper.getFieldValue;
import static tech.qianmi.flyingmybatis.automapper.MybatisHelper.setFieldValue;

/**
 * The core CRUD provider implementation
 *
 * @author yanan.zhang
 * @since 2021/2/18
 */
public class AutoMapperProvider implements ProviderMethodResolver {

    private static final String WHERE_COLUMN_EQUALS = "%s = #{%s}";

    private static final String WHERE_ID_IN = "%s in (%s)";

    private static final String SET_COLUMN = "%s = #{entity.%s}";

    private static final String TRUNCATE_TABLE = "truncate table %s";

    private static final String ENTITY = "entity";

    private static final String ENTITY_IS_NULL = "Entity is null";

    public static <E> String insert(@Param(ENTITY) E entity, ProviderContext context) {
        entity = getParam(entity, ENTITY);
        requireNonNull(entity, ENTITY_IS_NULL);

        TableInfo tableInfo = MetaDataCache.getTableInfo(context.getMapperType());
        if (tableInfo.getKeyType() == KeyType.UUID)
            setFieldValue(entity, tableInfo.getPrimaryKeyField(), randomUUID().toString());

        return new SQL().INSERT_INTO(tableInfo.getTableName())
                .INTO_COLUMNS(tableInfo.getBaseColumns())
                .INTO_VALUES(tableInfo.getIntoValues().replace(ENTITY_PLACEHOLDER, ENTITY))
                .toString();
    }

    public static <E> String insertAll(@Param("entities") Collection<E> entities, ProviderContext context) {
        checkArgument(entities, "Entities is null or empty");

        TableInfo tableInfo = MetaDataCache.getTableInfo(context.getMapperType());
        if (tableInfo.getKeyType() == KeyType.UUID)
            entities.forEach(entity -> setFieldValue(entity, tableInfo.getPrimaryKeyField(), randomUUID().toString()));

        return new SQL().INSERT_INTO(tableInfo.getTableName())
                .INTO_COLUMNS(tableInfo.getBaseColumns())
                .applyForEach(entities, (sql, entity, index) -> sql
                        .INTO_VALUES(tableInfo.getIntoValues().replace(ENTITY_PLACEHOLDER, "entities[" + index + "]"))
                        .ADD_ROW())
                .toString();
    }

    public static <K> String selectById(@Param("id") K id, ProviderContext context) {
        id = getParam(id, "id");
        requireNonNull(id, "ID is null");

        TableInfo tableInfo = MetaDataCache.getTableInfo(context.getMapperType());
        return new SQL()
                .SELECT("*")
                .FROM(tableInfo.getTableName())
                .WHERE(String.format(WHERE_COLUMN_EQUALS, tableInfo.getPrimaryKey(), "id"))
                .toString();
    }

    public static <K> String selectAllById(@Param("ids") Collection<K> ids, ProviderContext context) {
        checkArgument(ids, "IDs is null or empty");

        TableInfo tableInfo = MetaDataCache.getTableInfo(context.getMapperType());
        return new SQL()
                .SELECT("*")
                .FROM(tableInfo.getTableName())
                .WHERE(String.format(WHERE_ID_IN, tableInfo.getPrimaryKey(), getIdIn(ids)))
                .toString();
    }

    public static String selectAllByColumn(@Param("column") String column, @Param("value") Object value,
                                           ProviderContext context) {
        TableInfo tableInfo = MetaDataCache.getTableInfo(context.getMapperType());
        return new SQL()
                .SELECT("*")
                .FROM(tableInfo.getTableName())
                .WHERE(String.format(WHERE_COLUMN_EQUALS, column, "value"))
                .toString();
    }

    public static String selectAll(ProviderContext context) {
        TableInfo tableInfo = MetaDataCache.getTableInfo(context.getMapperType());
        return new SQL()
                .SELECT("*")
                .FROM(tableInfo.getTableName())
                .toString();
    }

    public static String countAll(ProviderContext context) {
        TableInfo tableInfo = MetaDataCache.getTableInfo(context.getMapperType());
        return new SQL()
                .SELECT("count(*)")
                .FROM(tableInfo.getTableName())
                .toString();
    }

    public static <K> String deleteById(@Param("id") K id, ProviderContext context) {
        id = getParam(id, "id");
        requireNonNull(id, "ID is null");

        TableInfo tableInfo = MetaDataCache.getTableInfo(context.getMapperType());
        return new SQL()
                .DELETE_FROM(tableInfo.getTableName())
                .WHERE(String.format(WHERE_COLUMN_EQUALS, tableInfo.getPrimaryKey(), "id"))
                .toString();
    }

    public static <K> String deleteAllById(@Param("ids") Collection<K> ids, ProviderContext context) {
        checkArgument(ids, "IDs is null or empty");

        TableInfo tableInfo = MetaDataCache.getTableInfo(context.getMapperType());
        return new SQL()
                .DELETE_FROM(tableInfo.getTableName())
                .WHERE(String.format(WHERE_ID_IN, tableInfo.getPrimaryKey(), getIdIn(ids)))
                .toString();
    }

    public static String deleteAll(ProviderContext context) {
        TableInfo tableInfo = MetaDataCache.getTableInfo(context.getMapperType());
        return String.format(TRUNCATE_TABLE, tableInfo.getTableName());
    }

    public static <E> String update(@Param(ENTITY) E entity, ProviderContext context) {
        entity = getParam(entity, ENTITY);
        requireNonNull(entity, ENTITY_IS_NULL);

        TableInfo tableInfo = MetaDataCache.getTableInfo(context.getMapperType());

        return buildUpdateSql(tableInfo, columnInfo -> true);
    }

    public static <E> String updateSelective(@Param(ENTITY) E entity, ProviderContext context) {
        E finalEntity = getParam(entity, ENTITY);
        TableInfo tableInfo = MetaDataCache.getTableInfo(context.getMapperType());

        requireNonNull(finalEntity, ENTITY_IS_NULL);
        requireNonNull(getFieldValue(finalEntity, tableInfo.getPrimaryKeyField()),
                "Updated entity ID is null");

        return buildUpdateSql(tableInfo, columnInfo -> nonNull(getFieldValue(finalEntity, columnInfo.getFieldName())));
    }

    private static String buildUpdateSql(TableInfo tableInfo, Predicate<ColumnInfo> selective) {
        SQL sql = new SQL().UPDATE(tableInfo.getTableName());
        for (ColumnInfo columnInfo : tableInfo.getColumnInfos()) {
            if (selective.test(columnInfo)) {
                sql.SET(String.format(SET_COLUMN, columnInfo.getColumnName(), columnInfo.getFieldName()));
            }
        }
        sql.WHERE(String.format(WHERE_COLUMN_EQUALS, tableInfo.getPrimaryKey(), "entity." + tableInfo.getPrimaryKeyField()));
        return sql.toString();
    }

    private static <K> String getIdIn(Collection<K> ids) {
        return IntStream.range(0, ids.size())
                .mapToObj(index -> "#{ids[" + index + "]}")
                .collect(Collectors.joining(", "));
    }

    // Just a workaround to get the parameter
    @SuppressWarnings("unchecked")
    private static <T> T getParam(Object paramMap, String key) {
        return ((Map<String, T>) paramMap).get(key);
    }

    private static <T> void checkArgument(Collection<T> arg, String message) {
        if (arg == null || arg.isEmpty())
            throw new IllegalArgumentException(message);
    }
}
