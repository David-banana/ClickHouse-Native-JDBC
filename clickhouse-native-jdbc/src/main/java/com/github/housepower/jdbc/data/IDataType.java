/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.housepower.jdbc.data;

import com.github.housepower.jdbc.misc.SQLLexer;
import com.github.housepower.jdbc.serde.BinaryDeserializer;
import com.github.housepower.jdbc.serde.BinarySerializer;

import java.io.IOException;
import java.sql.SQLException;

/**
 * IDataType is responsible for:
 * 1. represent data type attributions
 * 2. serialize and deserialize
 * 3. parse from sql text
 * 4. cast between inner type and other types
 * 5. format to string
 * @param <T> inner data type, 1:1 map to ClickHouse data type
 */
public interface IDataType<T> {

    // 1. represent data type attributions
    String name();

    default String[] getAliases() {
        return new String[0];
    }

    int sqlTypeId();

    Class<?> javaType();

    default Class<T> jdbcJavaType() {
        return (Class<T>) javaType();
    }

    boolean nullable();

    int getPrecision();

    int getScale();

    T defaultValue();

    // 2. serialize and deserialize
    void serializeBinary(T data, BinarySerializer serializer) throws SQLException, IOException;

    default void serializeBinaryBulk(T[] values, BinarySerializer serializer) throws SQLException, IOException {
        for (T value : values) {
            serializeBinary(value, serializer);
        }
    }

    T deserializeBinary(BinaryDeserializer deserializer) throws SQLException, IOException;

    T[] deserializeBinaryBulk(int rowCnt, BinaryDeserializer deserializer) throws SQLException, IOException;

    // 3. parse from sql text
    T deserializeTextQuoted(SQLLexer lexer) throws SQLException;

    // 4. cast between inner type and other types
    default <O> T castFrom(O other) {
        return (T) javaType().cast(other);
    }

    default <O> O castTo(T inner, Class<O> castToClazz) {
        return castToClazz.cast(inner);
    }

    // 5. format to string
    default <O> String format(O value) {
        return castTo(castFrom(value), String.class);
    }

    default boolean isSigned() {
        return false;
    }
}
