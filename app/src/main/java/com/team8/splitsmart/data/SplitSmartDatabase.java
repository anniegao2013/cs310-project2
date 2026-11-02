package com.team8.splitsmart.data;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.time.LocalDate;
import java.util.concurrent.TimeUnit;

import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Single access point to the Supabase (PostgreSQL) central repository through its REST API.
 * All calls are blocking and must run off the main thread.
 */
public class SplitSmartDatabase {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final String UNIQUE_VIOLATION = "23505";

    private static SplitSmartDatabase instance;

    private final OkHttpClient client;
    private final HttpUrl restUrl;
    private final String apiKey;
    private final Gson gson;

    private SplitSmartDatabase(String supabaseUrl, String apiKey) {
        if (supabaseUrl == null || supabaseUrl.isEmpty() || apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException("SUPABASE_URL and SUPABASE_ANON_KEY must be set in local.properties");
        }
        this.restUrl = HttpUrl.get(supabaseUrl).newBuilder().addPathSegments("rest/v1").build();
        this.apiKey = apiKey;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build();
        this.gson = new GsonBuilder()
                .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                .registerTypeAdapter(LocalDate.class, new LocalDateAdapter())
                .create();
    }

    public static synchronized void init(String supabaseUrl, String apiKey) {
        if (instance == null) {
            instance = new SplitSmartDatabase(supabaseUrl, apiKey);
        }
    }

    public static synchronized SplitSmartDatabase getInstance() {
        if (instance == null) {
            throw new IllegalStateException("SplitSmartDatabase.init() was not called");
        }
        return instance;
    }

    public Gson gson() {
        return gson;
    }

    /** GET rows. {@code params} are alternating PostgREST key/value pairs, e.g. "email", "eq.a@usc.edu". */
    public JsonArray select(String table, String... params) {
        Request request = baseRequest(url(table, params)).get().build();
        return execute(request).getAsJsonArray();
    }

    /** INSERT one row (or an array of rows) and return the inserted rows. */
    public JsonArray insert(String table, JsonElement body) {
        Request request = baseRequest(url(table))
                .header("Prefer", "return=representation")
                .post(RequestBody.create(body.toString(), JSON))
                .build();
        return execute(request).getAsJsonArray();
    }

    /** PATCH rows matching the filter params and return the updated rows. */
    public JsonArray update(String table, JsonObject body, String... params) {
        Request request = baseRequest(url(table, params))
                .header("Prefer", "return=representation")
                .patch(RequestBody.create(body.toString(), JSON))
                .build();
        return execute(request).getAsJsonArray();
    }

    /** DELETE rows matching the filter params. */
    public void delete(String table, String... params) {
        Request request = baseRequest(url(table, params)).delete().build();
        execute(request);
    }

    /** Call a PostgreSQL function; multi-row writes go through functions so they run in one transaction. */
    public JsonElement rpc(String function, JsonObject args) {
        HttpUrl url = restUrl.newBuilder().addPathSegment("rpc").addPathSegment(function).build();
        Request request = baseRequest(url)
                .post(RequestBody.create(args.toString(), JSON))
                .build();
        return execute(request);
    }

    private HttpUrl url(String table, String... params) {
        if (params.length % 2 != 0) {
            throw new IllegalArgumentException("params must be key/value pairs");
        }
        HttpUrl.Builder builder = restUrl.newBuilder().addPathSegment(table);
        for (int i = 0; i < params.length; i += 2) {
            builder.addQueryParameter(params[i], params[i + 1]);
        }
        return builder.build();
    }

    private Request.Builder baseRequest(HttpUrl url) {
        Request.Builder builder = new Request.Builder().url(url).header("apikey", apiKey);
        // Legacy anon keys are JWTs and also go in Authorization; new publishable keys only use apikey.
        if (apiKey.startsWith("eyJ")) {
            builder.header("Authorization", "Bearer " + apiKey);
        }
        return builder;
    }

    private JsonElement execute(Request request) {
        try (Response response = client.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw toException(response.code(), body);
            }
            if (body.isEmpty()) {
                return JsonNull.INSTANCE;
            }
            return JsonParser.parseString(body);
        } catch (IOException e) {
            throw new DataException("Could not reach the server. Check your internet connection.", e);
        }
    }

    private DataException toException(int status, String body) {
        String code = null;
        String message = "Server error (" + status + ")";
        try {
            JsonObject error = JsonParser.parseString(body).getAsJsonObject();
            if (error.has("code") && !error.get("code").isJsonNull()) {
                code = error.get("code").getAsString();
            }
            if (error.has("message") && !error.get("message").isJsonNull()) {
                message = error.get("message").getAsString();
            }
        } catch (RuntimeException ignored) {
            // Non-JSON error body; keep the generic message.
        }
        if (UNIQUE_VIOLATION.equals(code)) {
            return new DataException.UniqueViolation(message);
        }
        return new DataException(message);
    }

    /** Serializes LocalDate as YYYY-MM-DD, matching the PostgreSQL date type. */
    private static class LocalDateAdapter extends TypeAdapter<LocalDate> {
        @Override
        public void write(JsonWriter out, LocalDate value) throws IOException {
            if (value == null) {
                out.nullValue();
            } else {
                out.value(value.toString());
            }
        }

        @Override
        public LocalDate read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            return LocalDate.parse(in.nextString());
        }
    }
}
