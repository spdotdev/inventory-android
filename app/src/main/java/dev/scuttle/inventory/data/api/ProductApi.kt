package dev.scuttle.inventory.data.api

import dev.scuttle.inventory.data.dto.AmountRequest
import dev.scuttle.inventory.data.dto.CreateProductRequest
import dev.scuttle.inventory.data.dto.MoveProductRequest
import dev.scuttle.inventory.data.dto.ProductDeleteResponse
import dev.scuttle.inventory.data.dto.ProductListResponse
import dev.scuttle.inventory.data.dto.ProductResponse
import dev.scuttle.inventory.data.dto.UpdateProductRequest
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

interface ProductApi {
    @GET("households/{household}/shelves/{shelf}/products")
    suspend fun list(
        @Path("household") householdId: Long,
        @Path("shelf") shelfId: Long,
    ): ProductListResponse

    @POST("households/{household}/shelves/{shelf}/products")
    suspend fun create(
        @Path("household") householdId: Long,
        @Path("shelf") shelfId: Long,
        @Body body: CreateProductRequest,
    ): ProductResponse

    @PATCH("households/{household}/shelves/{shelf}/products/{product}")
    suspend fun update(
        @Path("household") householdId: Long,
        @Path("shelf") shelfId: Long,
        @Path("product") productId: Long,
        @Body body: UpdateProductRequest,
    ): ProductResponse

    @POST("households/{household}/shelves/{shelf}/products/{product}/add")
    suspend fun add(
        @Path("household") householdId: Long,
        @Path("shelf") shelfId: Long,
        @Path("product") productId: Long,
        @Body body: AmountRequest,
    ): ProductResponse

    @POST("households/{household}/shelves/{shelf}/products/{product}/remove")
    suspend fun remove(
        @Path("household") householdId: Long,
        @Path("shelf") shelfId: Long,
        @Path("product") productId: Long,
        @Body body: AmountRequest,
    ): ProductResponse

    @POST("households/{household}/shelves/{shelf}/products/{product}/move")
    suspend fun move(
        @Path("household") householdId: Long,
        @Path("shelf") shelfId: Long,
        @Path("product") productId: Long,
        @Body body: MoveProductRequest,
    ): ProductResponse

    @Multipart
    @POST("households/{household}/shelves/{shelf}/products/{product}/image")
    suspend fun uploadImage(
        @Path("household") householdId: Long,
        @Path("shelf") shelfId: Long,
        @Path("product") productId: Long,
        @Part image: MultipartBody.Part,
    ): ProductResponse

    // Returns the server-minted deletion_batch_id (ProductController::destroy
    // mints a batch-of-one) so the caller can offer Undo — a bodyless return
    // type here would silently discard it, as it used to.
    @DELETE("households/{household}/shelves/{shelf}/products/{product}")
    suspend fun delete(
        @Path("household") householdId: Long,
        @Path("shelf") shelfId: Long,
        @Path("product") productId: Long,
    ): ProductDeleteResponse
}
