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

package com.github.housepower.jdbc.data.type.complex;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import com.github.housepower.jdbc.connect.NativeContext.ServerContext;
import com.github.housepower.jdbc.data.IDataType;
import com.github.housepower.jdbc.misc.DateTimeUtil;
import com.github.housepower.jdbc.misc.SQLLexer;
import com.github.housepower.jdbc.misc.StringView;
import com.github.housepower.jdbc.misc.Validate;
import com.github.housepower.jdbc.serde.BinaryDeserializer;
import com.github.housepower.jdbc.serde.BinarySerializer;

public class DataTypeDateTime64 implements IDataType {

    public static DataTypeCreator creator = (lexer, serverContext) -> {
        if (lexer.isCharacter('(')) {
            Validate.isTrue(lexer.character() == '(');
            int scale = lexer.numberLiteral().intValue();
            Validate.isTrue(scale >= DataTypeDateTime64.MIN_SCALE && scale <= DataTypeDateTime64.MAX_SCALA,
                    "scale=" + scale + " out of range [" + DataTypeDateTime64.MIN_SCALE + "," + DataTypeDateTime64.MAX_SCALA + "]");
            if (lexer.isCharacter(',')) {
                Validate.isTrue(lexer.character() == ',');
                Validate.isTrue(lexer.isWhitespace());
                String dataTimeZone = lexer.stringLiteral();
                Validate.isTrue(lexer.character() == ')');
                return new DataTypeDateTime64("DateTime64(" + scale + ", '" + dataTimeZone + "')", scale, serverContext);
            }

            Validate.isTrue(lexer.character() == ')');
            return new DataTypeDateTime64("DateTime64(" + scale + ")", scale, serverContext);
        }
        return new DataTypeDateTime64("DateTime64", DataTypeDateTime64.DEFAULT_SCALE, serverContext);
    };

    private static final LocalDateTime EPOCH_LOCAL_DT = LocalDateTime.of(1970, 1, 1, 0, 0);
    public static final int NANOS_IN_SECOND = 1_000_000_000;
    public static final int MILLIS_IN_SECOND = 1000;
    public static final int[] POW_10 = {1, 10, 100, 1_000, 10_000, 100_000, 1_000_000, 10_000_000, 100_000_000, 1_000_000_000};
    public static final int MIN_SCALE = 0;
    public static final int MAX_SCALA = 9;
    public static final int DEFAULT_SCALE = 3;

    private final String name;
    private final int scale;
    private final ZoneId tz;
    private final ZonedDateTime defaultValue;

    public DataTypeDateTime64(String name, int scala, ServerContext serverContext) {
        this.name = name;
        this.scale = scala;
        this.tz = DateTimeUtil.chooseTimeZone(serverContext);
        this.defaultValue = EPOCH_LOCAL_DT.atZone(tz);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public int sqlTypeId() {
        return Types.TIMESTAMP;
    }

    @Override
    public Object defaultValue() {
        return defaultValue;
    }

    @Override
    public Class javaType() {
        return ZonedDateTime.class;
    }

    @Override
    public Class jdbcJavaType() {
        return Timestamp.class;
    }

    @Override
    public boolean nullable() {
        return false;
    }

    @Override
    public int getPrecision() {
        return 20;
    }

    @Override
    public int getScale() {
        return scale;
    }

    @Override
    public Object deserializeTextQuoted(SQLLexer lexer) throws SQLException {
        StringView dataTypeName = lexer.bareWord();
        Validate.isTrue(dataTypeName.checkEquals("toDateTime64"));
        Validate.isTrue(lexer.character() == '(');
        Validate.isTrue(lexer.character() == '\'');
        int year = lexer.numberLiteral().intValue();
        Validate.isTrue(lexer.character() == '-');
        int month = lexer.numberLiteral().intValue();
        Validate.isTrue(lexer.character() == '-');
        int day = lexer.numberLiteral().intValue();
        Validate.isTrue(lexer.isWhitespace());
        int hours = lexer.numberLiteral().intValue();
        Validate.isTrue(lexer.character() == ':');
        int minutes = lexer.numberLiteral().intValue();
        Validate.isTrue(lexer.character() == ':');
        BigDecimal _seconds = BigDecimal.valueOf(lexer.numberLiteral().doubleValue())
                .setScale(scale, BigDecimal.ROUND_HALF_UP);
        int second = _seconds.intValue();
        int nanos = _seconds.subtract(BigDecimal.valueOf(second)).movePointRight(9).intValue();
        Validate.isTrue(lexer.character() == '\'');
        Validate.isTrue(lexer.character() == ')');

        return ZonedDateTime.of(year, month, day, hours, minutes, second, nanos, tz);
    }

    @Override
    public void serializeBinary(Object data, BinarySerializer serializer) throws IOException {
        ZonedDateTime zdt = (ZonedDateTime) data;
        long epochSeconds = DateTimeUtil.toEpochSecond(zdt);
        int nanos = zdt.getNano();
        long value = (epochSeconds * NANOS_IN_SECOND + nanos) / POW_10[MAX_SCALA - scale];
        serializer.writeLong(value);
    }

    @Override
    public Object deserializeBinary(BinaryDeserializer deserializer) throws IOException {
        long value = deserializer.readLong() * POW_10[MAX_SCALA - scale];
        long epochSeconds = value / NANOS_IN_SECOND;
        int nanos = (int) (value % NANOS_IN_SECOND);

        return DateTimeUtil.toZonedDateTime(epochSeconds, nanos, tz);
    }

    @Override
    public Object[] deserializeBinaryBulk(int rows, BinaryDeserializer deserializer) throws IOException {
        ZonedDateTime[] data = new ZonedDateTime[rows];
        for (int row = 0; row < rows; row++) {
            data[row] = (ZonedDateTime) deserializeBinary(deserializer);
        }
        return data;
    }
}
