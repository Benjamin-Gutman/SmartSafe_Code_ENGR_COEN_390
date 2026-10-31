package com.example.safeui;

import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.Body;
import retrofit2.http.POST;

// Defines HTTP structure to be used by DataSync
public class ClientHTTP
{
    private ClientHTTP() {}

    private static final String BASE_URL = "https://enter_link/";

    public interface InventoryApi
    {

        @POST("command")
        Call<Models.Reply> command(@Body RequestBody commandLine);
    }

    public static InventoryApi create() {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build();

        return new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(InventoryApi.class);
    }
}
