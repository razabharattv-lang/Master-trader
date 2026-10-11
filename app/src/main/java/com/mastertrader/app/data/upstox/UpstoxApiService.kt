package com.mastertrader.app.data.upstox

import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface UpstoxApiService {
    @GET("v2/market-quote/quotes")
    suspend fun getFullMarketQuote(
        @Header("Authorization") bearerToken: String,
        @Query("instrument_key") instrumentKey: String
    ): UpstoxQuoteResponse

    @GET("v2/market-quote/quotes")
    suspend fun getLiveQuotes(
        @Header("Authorization") bearerToken: String,
        @Query("instrument_key") instrumentKeys: String
    ): UpstoxQuoteResponse

    @GET("v2/option/chain")
    suspend fun getOptionChain(
        @Header("Authorization") bearerToken: String,
        @Query("instrument_key") underlyingKey: String,
        @Query("expiry_date") expiryDate: String
    ): UpstoxOptionChainResponse
}

data class UpstoxQuoteResponse(
    @field:Json(name = "status") val status: String?,
    @field:Json(name = "data") val data: Map<String, QuoteData>?
)

data class QuoteData(
    @field:Json(name = "symbol") val symbol: String?,
    @field:Json(name = "last_price") val lastPrice: Double?,
    @field:Json(name = "ohlc") val ohlc: OhlcData?,
    @field:Json(name = "volume") val volume: Long?,
    @field:Json(name = "oi") val openInterest: Long?,
    @field:Json(name = "depth") val depth: DepthData?
)

data class OhlcData(
    @field:Json(name = "open") val open: Double?,
    @field:Json(name = "high") val high: Double?,
    @field:Json(name = "low") val low: Double?,
    @field:Json(name = "close") val close: Double?
)

data class DepthData(
    @field:Json(name = "buy") val buy: List<DepthItem>?,
    @field:Json(name = "sell") val sell: List<DepthItem>?
)

data class DepthItem(
    @field:Json(name = "quantity") val quantity: Int?,
    @field:Json(name = "price") val price: Double?,
    @field:Json(name = "orders") val orders: Int?
)

data class UpstoxOptionChainResponse(
    @field:Json(name = "status") val status: String?,
    @field:Json(name = "data") val data: List<OptionChainItem>?
)

data class OptionChainItem(
    @field:Json(name = "expiry_date") val expiryDate: String?,
    @field:Json(name = "strike_price") val strikePrice: Double?,
    @field:Json(name = "call_options") val callOptions: OptionContract?,
    @field:Json(name = "put_options") val putOptions: OptionContract?
)

data class OptionContract(
    @field:Json(name = "instrument_key") val instrumentKey: String?,
    @field:Json(name = "market_data") val marketData: OptionMarketData?,
    @field:Json(name = "option_greeks") val optionGreeks: OptionGreeks?
)

data class OptionMarketData(
    @field:Json(name = "ltp") val ltp: Double?,
    @field:Json(name = "volume") val volume: Long?,
    @field:Json(name = "oi") val openInterest: Long?,
    @field:Json(name = "oi_change") val oiChange: Double?
)

data class OptionGreeks(
    @field:Json(name = "delta") val delta: Double?,
    @field:Json(name = "gamma") val gamma: Double?,
    @field:Json(name = "theta") val theta: Double?,
    @field:Json(name = "vega") val vega: Double?,
    @field:Json(name = "iv") val iv: Double?
)

object UpstoxApiClient {
    private const val BASE_URL = "https://api.upstox.com/"

    private val client = OkHttpClient.Builder()
        .connectTimeout(45, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .build()

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    val service: UpstoxApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(UpstoxApiService::class.java)
    }
}
