package cloud.bjx.mm.android.repo

import cloud.bjx.mm.android.bean.TokenBean
import retrofit2.http.GET
import retrofit2.http.Query

interface ApiService {

    /** 获取 appKey、token */
    @GET("api/v1/tokens/generate")
    suspend fun fetchToken(@Query("userID") uid: String): TokenBean

}

object ServiceFactory {

    val apiService: ApiService by lazy {
        RetrofitFactory.retrofit("https://rtcauth.bjx.cloud").create(ApiService::class.java)
    }

}