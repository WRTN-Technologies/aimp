package io.wrtn.engine.lucene.query;

import static io.wrtn.engine.lucene.Constants.QUERY_TYPE_BOOST;

import com.google.gson.JsonObject;
import io.wrtn.util.GlobalExceptionHandler;
import io.wrtn.util.StatusCode;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.Query;

import java.util.Objects;

public final class BoostQueryBuilder {

    public static Query build(Query query, JsonObject queryObject) throws GlobalExceptionHandler {

        Objects.requireNonNull(query);

        if (queryObject.has(QUERY_TYPE_BOOST)) {
            float boost = queryObject.get(QUERY_TYPE_BOOST).getAsFloat();
            if (boost < 0) {
                throw new GlobalExceptionHandler(
                    "boost must be greater than zero. user value is " + boost,
                    StatusCode.BAD_REQUEST);
            }

            return new BoostQuery(query, boost);
        }
        return query;
    }
}
