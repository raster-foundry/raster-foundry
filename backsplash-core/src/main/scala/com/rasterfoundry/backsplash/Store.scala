package com.rasterfoundry.backsplash

import cats.data.{NonEmptyList => NEL}
import cats.effect.IO
import fs2.Stream
import geotrellis.vector.{Polygon, Projected}

import java.util.UUID

object Store {
  val oklahomaUUID = UUID.randomUUID
  val sentinelUUID = UUID.randomUUID
  val sentinelImages = List(
    BacksplashImage(
      "s3://rasterfoundry-production-data-us-east-1/public-cogs/0d8613cf-402b-4f14-9d49-c027bfbecb89_COG.tif",
      "POLYGON ((22.8894404639189588 -19.0711058398792694, 23.9430669354442784 -19.0711058398792694, 23.9430669354442784 -18.0663523131131640, 22.8894404639189588 -18.0663523131131640, 22.8894404639189588 -19.0711058398792694))",
      List(3, 2, 1)
    ),
    BacksplashImage(
      "s3://rasterfoundry-production-data-us-east-1/public-cogs/739c0a3b-89c8-4033-8a12-7918492abf83_COG.tif",
      "POLYGON ((24.1493879838667205 -19.0650892052675225, 24.8109855174224947 -19.0650892052675225, 24.8109855174224947 -18.0677241876274373, 24.1493879838667205 -18.0677241876274373, 24.1493879838667205 -19.0650892052675225))",
      List(3, 2, 1)
    ),
    BacksplashImage(
      "s3://rasterfoundry-production-data-us-east-1/public-cogs/1ea213b7-be05-4e49-9dff-2159ca1fe7fa_COG.tif",
      "POLYGON ((23.8338263141644049 -19.0588682645427241, 24.8166603106442345 -19.0588682645427241, 24.8166603106442345 -18.0507959024226317, 23.8338263141644049 -18.0507959024226317, 23.8338263141644049 -19.0588682645427241))",
      List(3, 2, 1)
    ),
    BacksplashImage(
      "s3://rasterfoundry-production-data-us-east-1/public-cogs/c242ab15-69fc-4a11-9611-b111b69f01b0_COG.tif",
      "POLYGON ((22.8800497316033216 -18.1676170695281805, 23.9275759003322328 -18.1676170695281805, 23.9275759003322328 -17.1634515313889686, 22.8800497316033216 -17.1634515313889686, 22.8800497316033216 -18.1676170695281805))",
      List(3, 2, 1)
    ),
    BacksplashImage(
      "s3://rasterfoundry-production-data-us-east-1/public-cogs/0a30f639-91ff-49d1-835b-d42b5fa9b496_COG.tif",
      "POLYGON ((23.8197545409383444 -18.1559995265154939, 24.8649780632993860 -18.1559995265154939, 24.8649780632993860 -17.1477233721893541, 23.8197545409383444 -17.1477233721893541, 23.8197545409383444 -18.1559995265154939))",
      List(3, 2, 1)
    ),
    BacksplashImage(
      "s3://rasterfoundry-production-data-us-east-1/public-cogs/1ac8bcd5-be6f-4930-942a-848a8bcf1030_COG.tif",
      "POLYGON ((24.1643939966126666 -18.1642196015565958, 25.0159134932046143 -18.1642196015565958, 25.0159134932046143 -17.1647506059132020, 24.1643939966126666 -17.1647506059132020, 24.1643939966126666 -18.1642196015565958))",
      List(3, 2, 1)
    ),
    BacksplashImage(
      "s3://rasterfoundry-production-data-us-east-1/public-cogs/aa127bef-8f99-4632-b656-c0a0a88f9130_COG.tif",
      "POLYGON ((25.0992372433307942 -19.0789070782413539, 26.1475510922213523 -19.0789070782413539, 26.1475510922213523 -18.0792832026268755, 25.0992372433307942 -18.0792832026268755, 25.0992372433307942 -19.0789070782413539))",
      List(3, 2, 1)
    ),
    BacksplashImage(
      "s3://rasterfoundry-production-data-us-east-1/public-cogs/fcee24a1-ab60-4021-b502-a2eaef1b91b6_COG.tif",
      "POLYGON ((22.8894404639189588 -19.0711058398792694, 23.9430669354442784 -19.0711058398792694, 23.9430669354442784 -18.0663523131131640, 22.8894404639189588 -18.0663523131131640, 22.8894404639189588 -19.0711058398792694))",
      List(3, 2, 1)
    ),
    BacksplashImage(
      "s3://rasterfoundry-production-data-us-east-1/public-cogs/e110f4bb-96de-4353-a5ec-2e0b8063813f_COG.tif",
      "POLYGON ((25.0992372433307942 -19.0789070782413539, 26.1475510922213523 -19.0789070782413539, 26.1475510922213523 -18.0792832026268755, 25.0992372433307942 -18.0792832026268755, 25.0992372433307942 -19.0789070782413539))",
      List(3, 2, 1)
    ),
    BacksplashImage(
      "s3://rasterfoundry-production-data-us-east-1/public-cogs/664d4b75-1044-4250-8ff3-f126636d5c04_COG.tif",
      "POLYGON ((25.1092522256482233 -18.1750230053831316, 26.1517902886892628 -18.1750230053831316, 26.1517902886892628 -17.1756961890987014, 25.1092522256482233 -17.1756961890987014, 25.1092522256482233 -18.1750230053831316))",
      List(3, 2, 1)
    ),
    BacksplashImage(
      "s3://rasterfoundry-production-data-us-east-1/public-cogs/adf51ea6-b876-4679-a584-0d8239f779e9_COG.tif",
      "POLYGON ((23.8338263141644049 -19.0588682645427241, 24.8080256658726661 -19.0588682645427241, 24.8080256658726661 -18.0509664526482858, 23.8338263141644049 -18.0509664526482858, 23.8338263141644049 -19.0588682645427241))",
      List(3, 2, 1)
    ),
    BacksplashImage(
      "s3://rasterfoundry-production-data-us-east-1/public-cogs/99c45581-d068-40a3-a87c-f3f2ce3144ee_COG.tif",
      "POLYGON ((22.8894404639189588 -19.0711058398792694, 23.9430669354442784 -19.0711058398792694, 23.9430669354442784 -18.0663523131131640, 22.8894404639189588 -18.0663523131131640, 22.8894404639189588 -19.0711058398792694))",
      List(3, 2, 1)
    ),
    BacksplashImage(
      "s3://rasterfoundry-production-data-us-east-1/public-cogs/c98486b0-215f-4c19-827f-eeea51d9ed5a_COG.tif",
      "POLYGON ((23.8338263141644049 -19.0588682645427241, 24.8067676083820530 -19.0588682645427241, 24.8067676083820530 -18.0509912693922665, 23.8338263141644049 -18.0509912693922665, 23.8338263141644049 -19.0588682645427241))",
      List(3, 2, 1)
    ),
    BacksplashImage(
      "s3://rasterfoundry-production-data-us-east-1/public-cogs/83b7e8ca-7db3-4c5e-b6c2-89242b9ce098_COG.tif",
      "POLYGON ((22.8894404639189588 -19.0711058398792694, 23.9430669354442784 -19.0711058398792694, 23.9430669354442784 -18.0663523131131640, 22.8894404639189588 -18.0663523131131640, 22.8894404639189588 -19.0711058398792694))",
      List(3, 2, 1)
    ),
    BacksplashImage(
      "s3://rasterfoundry-production-data-us-east-1/public-cogs/fbfd1591-70dc-411a-bea0-a6b41a6c26d0_COG.tif",
      "POLYGON ((24.1493879838667205 -19.0649253075382816, 24.7988488045030735 -19.0649253075382816, 24.7988488045030735 -18.0677241876274373, 24.1493879838667205 -18.0677241876274373, 24.1493879838667205 -19.0649253075382816))",
      List(3, 2, 1)
    ),
    BacksplashImage(
      "s3://rasterfoundry-production-data-us-east-1/public-cogs/e3a7f946-0d9e-4452-99d4-ca0084f38933_COG.tif",
      "POLYGON ((23.8197545409383444 -18.1559995265154939, 24.8639165224256438 -18.1559995265154939, 24.8639165224256438 -17.1477233721893541, 23.8197545409383444 -17.1477233721893541, 23.8197545409383444 -18.1559995265154939))",
      List(3, 2, 1)
    ),
    BacksplashImage(
      "s3://rasterfoundry-production-data-us-east-1/public-cogs/73528c3f-5b66-48e4-a841-179081f9ae92_COG.tif",
      "POLYGON ((23.8338263141644049 -19.0588682645427241, 24.8045198965798868 -19.0588682645427241, 24.8045198965798868 -18.0510355878936188, 23.8338263141644049 -18.0510355878936188, 23.8338263141644049 -19.0588682645427241))",
      List(3, 2, 1)
    ),
    BacksplashImage(
      "s3://rasterfoundry-production-data-us-east-1/public-cogs/608012f1-d230-449a-8683-cfe4089b6a04_COG.tif",
      "POLYGON ((22.8800497316033216 -18.1676170695281805, 23.9275759003322328 -18.1676170695281805, 23.9275759003322328 -17.1634515313889686, 22.8800497316033216 -17.1634515313889686, 22.8800497316033216 -18.1676170695281805))",
      List(3, 2, 1)
    ),
    BacksplashImage(
      "s3://rasterfoundry-production-data-us-east-1/public-cogs/485dbdeb-9b75-4429-9318-830a1b9d22da_COG.tif",
      "POLYGON ((24.1643939966126666 -18.1640781958360975, 25.0035827868306910 -18.1640781958360975, 25.0035827868306910 -17.1647506059132020, 24.1643939966126666 -17.1647506059132020, 24.1643939966126666 -18.1640781958360975))",
      List(3, 2, 1)
    ),
    BacksplashImage(
      "s3://rasterfoundry-production-data-us-east-1/public-cogs/46c4f7ee-e55a-4dfb-9168-4e7139deb203_COG.tif",
      "POLYGON ((22.8894404639189588 -19.0711058398792694, 23.9430669354442784 -19.0711058398792694, 23.9430669354442784 -18.0663523131131640, 22.8894404639189588 -18.0663523131131640, 22.8894404639189588 -19.0711058398792694))",
      List(3, 2, 1)
    ),
    BacksplashImage(
      "s3://rasterfoundry-production-data-us-east-1/public-cogs/f75fddff-6169-41bb-97fd-1276d56e0a50_COG.tif",
      "POLYGON ((24.4475182327470080 -18.1459915647549792, 24.8714861265918437 -18.1459915647549792, 24.8714861265918437 -17.1477233721893541, 24.4475182327470080 -17.1477233721893541, 24.4475182327470080 -18.1459915647549792))",
      List(3, 2, 1)
    ),
    BacksplashImage(
      "s3://rasterfoundry-production-data-us-east-1/public-cogs/c68f7baf-bb75-44fe-9ff5-e2eec8f62ce8_COG.tif",
      "POLYGON ((24.2235623508451781 -19.0527194084546281, 24.8919448695945178 -19.0527194084546281, 24.8919448695945178 -18.0497428346358113, 24.2235623508451781 -18.0497428346358113, 24.2235623508451781 -19.0527194084546281))",
      List(3, 2, 1)
    ),
    BacksplashImage(
      "s3://rasterfoundry-production-data-us-east-1/public-cogs/c4409643-ed4f-4b50-86a2-6f66e984129a_COG.tif",
      "POLYGON ((24.4441145934596165 -18.1685005331654637, 25.2114932105546714 -18.1685005331654637, 25.2114932105546714 -17.1711786682223639, 24.4441145934596165 -17.1711786682223639, 24.4441145934596165 -18.1685005331654637))",
      List(3, 2, 1)
    ),
    BacksplashImage(
      "s3://rasterfoundry-production-data-us-east-1/public-cogs/d9553c30-91bc-47fa-90d6-c2cf7ff97f41_COG.tif",
      "POLYGON ((24.1493879838667205 -19.0650924548794478, 24.8113278245077282 -19.0650924548794478, 24.8113278245077282 -18.0677241876274373, 24.1493879838667205 -18.0677241876274373, 24.1493879838667205 -19.0650924548794478))",
      List(3, 2, 1)
    ),
    BacksplashImage(
      "s3://rasterfoundry-production-data-us-east-1/public-cogs/558fb723-07e9-4fdf-b622-3b91c30a154c_COG.tif",
      "POLYGON ((23.8338263141644049 -19.0588682645427241, 24.8171336655135377 -19.0588682645427241, 24.8171336655135377 -18.0507865416253850, 23.8338263141644049 -18.0507865416253850, 23.8338263141644049 -19.0588682645427241))",
      List(3, 2, 1)
    ),
    BacksplashImage(
      "s3://rasterfoundry-production-data-us-east-1/public-cogs/294e1b20-a4bd-4486-9886-6fc021232546_COG.tif",
      "POLYGON ((22.8894404639189588 -19.0711058398792694, 23.9430669354442784 -19.0711058398792694, 23.9430669354442784 -18.0663523131131640, 22.8894404639189588 -18.0663523131131640, 22.8894404639189588 -19.0711058398792694))",
      List(3, 2, 1)
    ),
    BacksplashImage(
      "s3://rasterfoundry-production-data-us-east-1/public-cogs/75175d28-3519-4f55-871f-ef86855ef74e_COG.tif",
      "POLYGON ((23.8338263141644049 -19.0588682645427241, 24.8107694228636504 -19.0588682645427241, 24.8107694228636504 -18.0509123002123921, 23.8338263141644049 -18.0509123002123921, 23.8338263141644049 -19.0588682645427241))",
      List(3, 2, 1)
    ),
    BacksplashImage(
      "s3://rasterfoundry-production-data-us-east-1/public-cogs/59df693f-a614-4608-8cee-639620279ef0_COG.tif",
      "POLYGON ((22.8894404639189588 -19.0711058398792694, 23.9430669354442784 -19.0711058398792694, 23.9430669354442784 -18.0663523131131640, 22.8894404639189588 -18.0663523131131640, 22.8894404639189588 -19.0711058398792694))",
      List(3, 2, 1)
    ),
    BacksplashImage(
      "s3://rasterfoundry-production-data-us-east-1/public-cogs/c88549e1-9681-47a6-8069-1647e4d92506_COG.tif",
      "POLYGON ((22.8894404639189588 -19.0711058398792694, 23.9430669354442784 -19.0711058398792694, 23.9430669354442784 -18.0663523131131640, 22.8894404639189588 -18.0663523131131640, 22.8894404639189588 -19.0711058398792694))",
      List(3, 2, 1)
    ),
    BacksplashImage(
      "s3://rasterfoundry-production-data-us-east-1/public-cogs/110c9a07-70d1-4c51-9fb1-e3e6b0a47ef6_COG.tif",
      "POLYGON ((23.8338263141644049 -19.0588682645427241, 24.8270726151260597 -19.0588682645427241, 24.8270726151260597 -18.0505897266734259, 23.8338263141644049 -18.0505897266734259, 23.8338263141644049 -19.0588682645427241))",
      List(3, 2, 1)
    ),
    BacksplashImage(
      "s3://rasterfoundry-production-data-us-east-1/public-cogs/e67e62de-77a3-47b3-b060-157b2322ae00_COG.tif",
      "POLYGON ((24.4662967582290705 -18.1456538010586002, 24.8714861265918437 -18.1456538010586002, 24.8714861265918437 -17.1477233721893541, 24.4662967582290705 -17.1477233721893541, 24.4662967582290705 -18.1456538010586002))",
      List(3, 2, 1)
    ),
    BacksplashImage(
      "s3://rasterfoundry-production-data-us-east-1/public-cogs/6faa3581-b6f2-42ef-b45a-3c89d1b527be_COG.tif",
      "POLYGON ((24.2423696990658719 -19.0523896379473037, 24.8919448695945178 -19.0523896379473037, 24.8919448695945178 -18.0497428346358113, 24.2423696990658719 -18.0497428346358113, 24.2423696990658719 -19.0523896379473037))",
      List(3, 2, 1)
    ),
    BacksplashImage(
      "s3://rasterfoundry-production-data-us-east-1/public-cogs/6279eab9-be4e-4531-a767-98251ba63b08_COG.tif",
      "POLYGON ((24.4627566624771902 -18.1685005331654637, 25.2114932105546714 -18.1685005331654637, 25.2114932105546714 -17.1713925242838492, 24.4627566624771902 -17.1713925242838492, 24.4627566624771902 -18.1685005331654637))",
      List(3, 2, 1)
    ),
    BacksplashImage(
      "s3://rasterfoundry-production-data-us-east-1/public-cogs/39a23e01-0777-4802-b781-b3db036744f3_COG.tif",
      "POLYGON ((25.1092522256482233 -18.1750230053831316, 26.1517902886892628 -18.1750230053831316, 26.1517902886892628 -17.1756961890987014, 25.1092522256482233 -17.1756961890987014, 25.1092522256482233 -18.1750230053831316))",
      List(3, 2, 1)
    ),
    BacksplashImage(
      "s3://rasterfoundry-production-data-us-east-1/public-cogs/33a80e5b-6fca-41cc-915d-d9a7f53fefcc_COG.tif",
      "POLYGON ((25.0992372433307942 -19.0789070782413539, 26.1475510922213523 -19.0789070782413539, 26.1475510922213523 -18.0792832026268755, 25.0992372433307942 -18.0792832026268755, 25.0992372433307942 -19.0789070782413539))",
      List(3, 2, 1)
    ),
    BacksplashImage(
      "s3://rasterfoundry-production-data-us-east-1/public-cogs/b6a8c5cd-4219-4b8d-bef7-438d7392113c_COG.tif",
      "POLYGON ((22.8894404639189588 -19.0711058398792694, 23.9430669354442784 -19.0711058398792694, 23.9430669354442784 -18.0663523131131640, 22.8894404639189588 -18.0663523131131640, 22.8894404639189588 -19.0711058398792694))",
      List(3, 2, 1)
    ),
    BacksplashImage(
      "s3://rasterfoundry-production-data-us-east-1/public-cogs/6be2edd8-a6c4-43da-90c3-d6576ab1f1d6_COG.tif",
      "POLYGON ((22.8800497316033216 -18.1676170695281805, 23.9275759003322328 -18.1676170695281805, 23.9275759003322328 -17.1634515313889686, 22.8800497316033216 -17.1634515313889686, 22.8800497316033216 -18.1676170695281805))",
      List(3, 2, 1)
    ),
    BacksplashImage(
      "s3://rasterfoundry-production-data-us-east-1/public-cogs/5c6c680e-939e-49f9-a745-f00e556af151_COG.tif",
      "POLYGON ((23.8338263141644049 -19.0588682645427241, 24.8107568753766898 -19.0588682645427241, 24.8107568753766898 -18.0509125479458241, 23.8338263141644049 -18.0509125479458241, 23.8338263141644049 -19.0588682645427241))",
      List(3, 2, 1)
    ),
    BacksplashImage(
      "s3://rasterfoundry-production-data-us-east-1/public-cogs/d9a60202-2b66-43db-aaf3-9e6c4a4cc702_COG.tif",
      "POLYGON ((22.8894404639189588 -19.0711058398792694, 23.9430669354442784 -19.0711058398792694, 23.9430669354442784 -18.0663523131131640, 22.8894404639189588 -18.0663523131131640, 22.8894404639189588 -19.0711058398792694))",
      List(3, 2, 1)
    ),
    BacksplashImage(
      "s3://rasterfoundry-production-data-us-east-1/public-cogs/178618f7-1f6d-4e47-bb7a-31f579163b82_COG.tif",
      "POLYGON ((23.8338263141644049 -19.0588682645427241, 24.7841043048821064 -19.0588682645427241, 24.7841043048821064 -18.0662486360976118, 23.8338263141644049 -18.0662486360976118, 23.8338263141644049 -19.0588682645427241))",
      List(3, 2, 1)
    ),
    BacksplashImage(
      "s3://rasterfoundry-production-data-us-east-1/public-cogs/e914122b-42a1-4396-aac2-6c55b2c59285_COG.tif",
      "POLYGON ((23.8338263141644049 -19.0588682645427241, 24.8116610754541504 -19.0588682645427241, 24.8116610754541504 -18.0508946936290613, 23.8338263141644049 -18.0508946936290613, 23.8338263141644049 -19.0588682645427241))",
      List(3, 2, 1)
    ),
    BacksplashImage(
      "s3://rasterfoundry-production-data-us-east-1/public-cogs/04bbe06e-9b31-4698-a76a-0fe88b7bd2aa_COG.tif",
      "POLYGON ((22.8894404639189588 -19.0711058398792694, 23.9430669354442784 -19.0711058398792694, 23.9430669354442784 -18.0663523131131640, 22.8894404639189588 -18.0663523131131640, 22.8894404639189588 -19.0711058398792694))",
      List(3, 2, 1)
    ),
    BacksplashImage(
      "s3://rasterfoundry-production-data-us-east-1/public-cogs/ecc0b07c-ebea-4ea3-adac-bd7b9313da90_COG.tif",
      "POLYGON ((22.8894404639189588 -19.0711058398792694, 23.9430669354442784 -19.0711058398792694, 23.9430669354442784 -18.0663523131131640, 22.8894404639189588 -18.0663523131131640, 22.8894404639189588 -19.0711058398792694))",
      List(3, 2, 1)
    ),
    BacksplashImage(
      "s3://rasterfoundry-production-data-us-east-1/public-cogs/11ab8731-7337-474e-ac9f-9657740fd695_COG.tif",
      "POLYGON ((23.8338263141644049 -19.0588682645427241, 24.8302175396797722 -19.0588682645427241, 24.8302175396797722 -18.0505273431220878, 23.8338263141644049 -18.0505273431220878, 23.8338263141644049 -19.0588682645427241))",
      List(3, 2, 1)
    ),
    BacksplashImage(
      "s3://rasterfoundry-production-data-us-east-1/public-cogs/a6e9199c-5a6e-4c48-b3e9-0ca6e0eeadde_COG.tif",
      "POLYGON ((23.8338263141644049 -19.0588682645427241, 24.8154235462596482 -19.0588682645427241, 24.8154235462596482 -18.0508203544973824, 23.8338263141644049 -18.0508203544973824, 23.8338263141644049 -19.0588682645427241))",
      List(3, 2, 1)
    ),
    BacksplashImage(
      "s3://rasterfoundry-production-data-us-east-1/public-cogs/128e622a-9c65-4059-a802-b863ea268a55_COG.tif",
      "POLYGON ((22.8894404639189588 -19.0711058398792694, 23.9430669354442784 -19.0711058398792694, 23.9430669354442784 -18.0663523131131640, 22.8894404639189588 -18.0663523131131640, 22.8894404639189588 -19.0711058398792694))",
      List(3, 2, 1)
    ),
    BacksplashImage(
      "s3://rasterfoundry-production-data-us-east-1/public-cogs/d85f072f-9e6e-4bee-bda2-f5544f9dc421_COG.tif",
      "POLYGON ((22.8894404639189588 -19.0711058398792694, 23.9430669354442784 -19.0711058398792694, 23.9430669354442784 -18.0663523131131640, 22.8894404639189588 -18.0663523131131640, 22.8894404639189588 -19.0711058398792694))",
      List(3, 2, 1)
    ),
    BacksplashImage(
      "s3://rasterfoundry-production-data-us-east-1/public-cogs/19981a39-0192-431d-b2a2-dc5ff0683be6_COG.tif",
      "POLYGON ((23.8338263141644049 -19.0588682645427241, 24.8248881025335670 -19.0588682645427241, 24.8248881025335670 -18.0506330290989503, 23.8338263141644049 -18.0506330290989503, 23.8338263141644049 -19.0588682645427241))",
      List(3, 2, 1)
    ),
    BacksplashImage(
      "s3://rasterfoundry-production-catalogs-us-east-1/layers",
      "POLYGON ((22.8894404639189588 -19.0711058398792694, 23.9430669354442784 -19.0711058398792694, 23.9430669354442784 -18.0663523131131640, 22.8894404639189588 -18.0663523131131640, 22.8894404639189588 -19.0711058398792694))",
      List(3, 2, 1)
    ),
    BacksplashImage(
      "s3://rasterfoundry-production-catalogs-us-east-1/layers",
      "POLYGON ((23.8338263141644049 -19.0588682645427241, 24.8137314088847383 -19.0588682645427241, 24.8137314088847383 -18.0508537969225920, 23.8338263141644049 -18.0508537969225920, 23.8338263141644049 -19.0588682645427241))",
      List(3, 2, 1)
    ),
    BacksplashImage(
      "s3://rasterfoundry-production-catalogs-us-east-1/layers",
      "POLYGON ((24.1493879838667205 -19.0650526510835228, 24.8079570506115736 -19.0650526510835228, 24.8079570506115736 -18.0677241876274373, 24.1493879838667205 -18.0677241876274373, 24.1493879838667205 -19.0650526510835228))",
      List(3, 2, 1)
    ),
    BacksplashImage(
      "s3://rasterfoundry-production-catalogs-us-east-1/layers",
      "POLYGON ((23.8338263141644049 -19.0588682645427241, 24.8192978224856020 -19.0588682645427241, 24.8192978224856020 -18.0507437297076194, 23.8338263141644049 -18.0507437297076194, 23.8338263141644049 -19.0588682645427241))",
      List(3, 2, 1)
    ),
    BacksplashImage(
      "s3://rasterfoundry-production-catalogs-us-east-1/layers",
      "POLYGON ((22.8894404639189588 -19.0711058398792694, 23.9430669354442784 -19.0711058398792694, 23.9430669354442784 -18.0663523131131640, 22.8894404639189588 -18.0663523131131640, 22.8894404639189588 -19.0711058398792694))",
      List(3, 2, 1)
    ),
    BacksplashImage(
      "s3://rasterfoundry-production-catalogs-us-east-1/layers",
      "POLYGON ((23.8338263141644049 -19.0588682645427241, 24.8175077471580892 -19.0588682645427241, 24.8175077471580892 -18.0507791431783922, 23.8338263141644049 -18.0507791431783922, 23.8338263141644049 -19.0588682645427241))",
      List(3, 2, 1)
    ),
    BacksplashImage(
      "s3://rasterfoundry-production-catalogs-us-east-1/layers",
      "POLYGON ((22.8894404639189588 -19.0711058398792694, 23.9430669354442784 -19.0711058398792694, 23.9430669354442784 -18.0663523131131640, 22.8894404639189588 -18.0663523131131640, 22.8894404639189588 -19.0711058398792694))",
      List(3, 2, 1)
    )
  )
  val sentinelImageStreams = sentinelImages map { im =>
    Stream.eval { IO.pure { im } }
  }
  val sentinelImageStream = sentinelImageStreams.reduce(_ ++ _)
  val projects: Map[UUID, BacksplashMosaic] = Map(
    oklahomaUUID -> (Stream
      .eval {
        IO.pure {
          BacksplashImage(
            "s3://radiant-nasa-iserv/2014/09/20/IPR201409201521563491N09706W/IPR201409201521563491N09706W-COG.tif",
            "POLYGON ((-97.1725628915165345 34.8130631368108112, -96.9454161960794210 34.8130631368108112, -96.9454161960794210 35.0064019519968568, -97.1725628915165345 35.0064019519968568, -97.1725628915165345 34.8130631368108112))",
            List(2, 1, 0)
          )
        }
      })
      .repeat
      .take(100),
    sentinelUUID -> sentinelImageStream.take(10)
  )

  implicit val projStore: ProjectStore[Map[UUID, BacksplashMosaic]] =
    new ProjectStore[Map[UUID, BacksplashMosaic]] {
      def read(self: Map[UUID, BacksplashMosaic],
               projId: UUID,
               window: Option[Projected[Polygon]],
               bandOverride: Option[BandOverride],
               imageSubset: Option[NEL[UUID]]) = self.get(projId).get map {
        im =>
          bandOverride map { bovr =>
            im.copy(subsetBands = List(bovr.red, bovr.green, bovr.blue))
          } getOrElse im
      }
    }
}
