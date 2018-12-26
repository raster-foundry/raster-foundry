package com.rasterfoundry.backsplash

import cats.effect.IO
import org.scalacheck._

import java.util.UUID

object BacksplashImageGen {

  val images: Gen[BacksplashImage] = Gen.oneOf(
    BacksplashImage.fromWkt(
      UUID.randomUUID,
      "s3://radiant-nasa-iserv/2014/09/20/IPR201409201521563491N09706W/IPR201409201521563491N09706W-COG.tif",
      "POLYGON ((-97.1725628915165345 34.8130631368108112, -96.9454161960794210 34.8130631368108112, -96.9454161960794210 35.0064019519968568, -97.1725628915165345 35.0064019519968568, -97.1725628915165345 34.8130631368108112))",
      List(2, 1, 0)
    ),
    BacksplashImage.fromWkt(
      UUID.randomUUID,
      "s3://radiant-nasa-iserv/2014/09/20/IPR201409201521553492N09708W/IPR201409201521553492N09708W-COG.tif",
      "POLYGON ((-97.1906985910503778 34.8270184863941665, -96.9637565621053739 34.8270184863941665, -96.9637565621053739 35.0203629523948123, -97.1906985910503778 35.0203629523948123, -97.1906985910503778 34.8270184863941665))",
      List(2, 1, 0)
    ),
    BacksplashImage.fromWkt(
      UUID.randomUUID,
      "s3://radiant-nasa-iserv/2014/09/20/IPR201409201521553495N09711W/IPR201409201521553495N09711W-COG.tif",
      "POLYGON ((-97.2281809067166449 34.8551351701124901, -97.0008045066477251 34.8551351701124901, -97.0008045066477251 35.0487009530707923, -97.2281809067166449 35.0487009530707923, -97.2281809067166449 34.8551351701124901))",
      List(2, 1, 0)
    ),
    BacksplashImage.fromWkt(
      UUID.randomUUID,
      "s3://radiant-nasa-iserv/2014/09/20/IPR201409201521543498N09715W/IPR201409201521543498N09715W-COG.tif",
      "POLYGON ((-97.2654809170823995 34.8836602267666578, -97.0380900990747932 34.8836602267666578, -97.0380900990747932 35.0770269539757535, -97.2654809170823995 35.0770269539757535, -97.2654809170823995 34.8836602267666578))",
      List(2, 1, 0)
    ),
    BacksplashImage.fromWkt(
      UUID.randomUUID,
      "s3://radiant-nasa-iserv/2014/09/20/IPR201409201521543497N09713W/IPR201409201521543497N09713W-COG.tif",
      "POLYGON ((-97.2471059118776395 34.8697146102591304, -97.0197214250801210 34.8697146102591304, -97.0197214250801210 35.0630759535860435, -97.2471059118776395 35.0630759535860435, -97.2471059118776395 34.8697146102591304))",
      List(2, 1, 0)
    ),
    BacksplashImage.fromWkt(
      UUID.randomUUID,
      "s3://radiant-nasa-iserv/2014/09/05/IPR201409052106073489N09695W/IPR201409052106073489N09695W-COG.tif",
      "POLYGON ((-97.0628513750294672 34.7908309703937988, -96.8287524063388787 34.7908309703937988, -96.8287524063388787 34.9895453945081059, -97.0628513750294672 34.9895453945081059, -97.0628513750294672 34.7908309703937988))",
      List(2, 1, 0)
    ),
    BacksplashImage.fromWkt(
      UUID.randomUUID,
      "s3://radiant-nasa-iserv/2014/09/05/IPR201409052106073486N09691W/IPR201409052106073486N09691W-COG.tif",
      "POLYGON ((-97.0257163652320003 34.7634380282935211, -96.7916268893966958 34.7634380282935211, -96.7916268893966958 34.9621443944173436, -97.0257163652320003 34.9621443944173436, -97.0257163652320003 34.7634380282935211))",
      List(2, 1, 0)
    ),
    BacksplashImage.fromWkt(
      UUID.randomUUID,
      "s3://radiant-nasa-iserv/2014/09/05/IPR201409052106073488N09693W/IPR201409052106073488N09693W-COG.tif",
      "POLYGON ((-97.0442793702475228 34.7771370487292160, -96.8101852061633679 34.7771370487292160, -96.8101852061633679 34.9758473944633579, -97.0442793702475228 34.9758473944633579, -97.0442793702475228 34.7771370487292160))",
      List(2, 1, 0)
    ),
    BacksplashImage.fromWkt(
      UUID.randomUUID,
      "s3://radiant-nasa-iserv/2014/09/05/IPR201409052106063490N09696W/IPR201409052106063490N09696W-COG.tif",
      "POLYGON ((-97.0819943801297143 34.8049345923894293, -96.8478890755914961 34.8049345923894293, -96.8478890755914961 35.0036543946748537, -97.0819943801297143 35.0036543946748537, -97.0819943801297143 34.8049345923894293))",
      List(2, 1, 0)
    ),
    BacksplashImage.fromWkt(
      UUID.randomUUID,
      "s3://radiant-nasa-iserv/2014/09/05/IPR201409052106063492N09698W/IPR201409052106063492N09698W-COG.tif",
      "POLYGON ((-97.1005823851498775 34.8186194136557390, -96.8664709795259995 34.8186194136557390, -96.8664709795259995 35.0173443948350851, -97.1005823851498775 35.0173443948350851, -97.1005823851498775 34.8186194136557390))",
      List(2, 1, 0)
    ),
    BacksplashImage.fromWkt(
      UUID.randomUUID,
      "s3://radiant-nasa-iserv/2014/09/05/IPR201409052106063493N09700W/IPR201409052106063493N09700W-COG.tif",
      "POLYGON ((-97.1191783902901165 34.8323002971317166, -96.8850609568694097 34.8323002971317166, -96.8850609568694097 35.0310303949939055, -97.1191783902901165 35.0310303949939055, -97.1191783902901165 34.8323002971317166))",
      List(2, 1, 0)
    ),
    BacksplashImage.fromWkt(
      UUID.randomUUID,
      "s3://radiant-nasa-iserv/2014/09/05/IPR201409052106053496N09704W/IPR201409052106053496N09704W-COG.tif",
      "POLYGON ((-97.1569604005394183 34.8600606212477615, -96.9226134225125691 34.8600606212477615, -96.9226134225125691 35.0588023954307744, -97.1569604005394183 35.0588023954307744, -97.1569604005394183 34.8600606212477615))",
      List(2, 1, 0)
    ),
    BacksplashImage.fromWkt(
      UUID.randomUUID,
      "s3://radiant-nasa-iserv/2014/09/05/IPR201409052106053495N09702W/IPR201409052106053495N09702W-COG.tif",
      "POLYGON ((-97.1383473953960248 34.8463887203470932, -96.9042222137494349 34.8463887203470932, -96.9042222137494349 35.0451253952747450, -97.1383473953960248 35.0451253952747450, -97.1383473953960248 34.8463887203470932))",
      List(2, 1, 0)
    ),
    BacksplashImage.fromWkt(
      UUID.randomUUID,
      "s3://radiant-nasa-iserv/2014/09/05/IPR201409052106053497N09706W/IPR201409052106053497N09706W-COG.tif",
      "POLYGON ((-97.1759296110959099 34.8741423957413019, -96.9417922659973357 34.8741423957413019, -96.9417922659973357 35.0728893955899679, -97.1759296110959099 35.0728893955899679, -97.1759296110959099 34.8741423957413019))",
      List(2, 1, 0)
    ),
    BacksplashImage.fromWkt(
      UUID.randomUUID,
      "s3://radiant-nasa-iserv/2014/09/05/IPR201409052106043501N09712W/IPR201409052106043501N09712W-COG.tif",
      "POLYGON ((-97.2326244216476567 34.9155249661612359, -96.9982460117648344 34.9155249661612359, -96.9982460117648344 35.1142933966419619, -97.2326244216476567 35.1142933966419619, -97.2326244216476567 34.9155249661612359))",
      List(2, 1, 0)
    ),
    BacksplashImage.fromWkt(
      UUID.randomUUID,
      "s3://radiant-nasa-iserv/2014/09/05/IPR201409052106043499N09708W/IPR201409052106043499N09708W-COG.tif",
      "POLYGON ((-97.1945596080615672 34.8878018608510345, -96.9604133858952650 34.8878018608510345, -96.9604133858952650 35.0865563959801960, -97.1945596080615672 35.0865563959801960, -97.1945596080615672 34.8878018608510345))",
      List(2, 1, 0)
    ),
    BacksplashImage.fromWkt(
      UUID.randomUUID,
      "s3://radiant-nasa-iserv/2014/09/05/IPR201409052106043500N09710W/IPR201409052106043500N09710W-COG.tif",
      "POLYGON ((-97.2134134162980814 34.9014596152741348, -96.9790440263522413 34.9014596152741348, -96.9790440263522413 35.1002203962503216, -97.2134134162980814 35.1002203962503216, -97.2134134162980814 34.9014596152741348))",
      List(2, 1, 0)
    ),
    BacksplashImage.fromWkt(
      UUID.randomUUID,
      "s3://radiant-nasa-iserv/2014/07/10/IPR201407101957193502N09712W/IPR201407101957193502N09712W-COG.tif",
      "POLYGON ((-97.2330666252158977 34.9279650705798161, -96.9988604008403286 34.9279650705798161, -96.9988604008403286 35.1210639918471728, -97.2330666252158977 35.1210639918471728, -97.2330666252158977 34.9279650705798161))",
      List(2, 1, 0)
    ),
    BacksplashImage.fromWkt(
      UUID.randomUUID,
      "s3://radiant-nasa-iserv/2014/07/10/IPR201407101957193504N09713W/IPR201407101957193504N09713W-COG.tif",
      "POLYGON ((-97.2510756304749009 34.9422661677868689, -97.0168622462293513 34.9422661677868689, -97.0168622462293513 35.1353709922413202, -97.2510756304749009 35.1353709922413202, -97.2510756304749009 34.9422661677868689))",
      List(2, 1, 0)
    ),
    BacksplashImage.fromWkt(
      UUID.randomUUID,
      "s3://radiant-nasa-iserv/2013/09/16/IPR201309161734133475N09694W/IPR201309161734133475N09694W-COG.tif",
      "POLYGON ((-97.0500990542665960 34.6526907837579614, -96.8216722711807307 34.6526907837579614, -96.8216722711807307 34.8456829412127860, -97.0500990542665960 34.8456829412127860, -97.0500990542665960 34.6526907837579614))",
      List(2, 1, 0)
    ),
    BacksplashImage.fromWkt(
      UUID.randomUUID,
      "s3://radiant-nasa-iserv/2013/09/16/IPR201309161734133476N09695W/IPR201309161734133476N09695W-COG.tif",
      "POLYGON ((-97.0681720591346391 34.6668748088858720, -96.8397417548138151 34.6668748088858720, -96.8397417548138151 34.8598699413452877, -97.0681720591346391 34.8598699413452877, -97.0681720591346391 34.6668748088858720))",
      List(2, 1, 0)
    ),
    BacksplashImage.fromWkt(
      UUID.randomUUID,
      "s3://radiant-nasa-iserv/2013/09/16/IPR201309161734133478N09697W/IPR201309161734133478N09697W-COG.tif",
      "POLYGON ((-97.0867990641998233 34.6814843797951156, -96.8583623335316588 34.6814843797951156, -96.8583623335316588 34.8744849417169291, -97.0867990641998233 34.8744849417169291, -97.0867990641998233 34.6814843797951156))",
      List(2, 1, 0)
    ),
    BacksplashImage.fromWkt(
      UUID.randomUUID,
      "s3://radiant-nasa-iserv/2013/09/16/IPR201309161734123479N09699W/IPR201309161734123479N09699W-COG.tif",
      "POLYGON ((-97.1048840690726820 34.6956642301144029, -96.8762314924121171 34.6956642301144029, -96.8762314924121171 34.8886689419634948, -97.1048840690726820 34.8886689419634948, -97.1048840690726820 34.6956642301144029))",
      List(2, 1, 0)
    ),
    BacksplashImage.fromWkt(
      UUID.randomUUID,
      "s3://radiant-nasa-iserv/2013/09/16/IPR201309161734123482N09703W/IPR201309161734123482N09703W-COG.tif",
      "POLYGON ((-97.1416200792544799 34.7244479395650387, -96.9129564953643694 34.7244479395650387, -96.9129564953643694 34.9174619425719044, -97.1416200792544799 34.9174619425719044, -97.1416200792544799 34.7244479395650387))",
      List(2, 1, 0)
    ),
    BacksplashImage.fromWkt(
      UUID.randomUUID,
      "s3://radiant-nasa-iserv/2013/09/16/IPR201309161734123481N09701W/IPR201309161734123481N09701W-COG.tif",
      "POLYGON ((-97.1229750741824063 34.7098421003179070, -96.8943164198250884 34.7098421003179070, -96.8943164198250884 34.9028519423261514, -97.1229750741824063 34.9028519423261514, -97.1229750741824063 34.7098421003179070))",
      List(2, 1, 0)
    ),
    BacksplashImage.fromWkt(
      UUID.randomUUID,
      "s3://radiant-nasa-iserv/2013/09/16/IPR201309161734113484N09705W/IPR201309161734113484N09705W-COG.tif",
      "POLYGON ((-97.1597230843691051 34.7386228468355256, -96.9310534667011723 34.7386228468355256, -96.9310534667011723 34.9316419429297937, -97.1597230843691051 34.9316419429297937, -97.1597230843691051 34.7386228468355256))",
      List(2, 1, 0)
    ),
    BacksplashImage.fromWkt(
      UUID.randomUUID,
      "s3://radiant-nasa-iserv/2013/09/16/IPR201309161734113486N09708W/IPR201309161734113486N09708W-COG.tif",
      "POLYGON ((-97.1962841333764089 34.7673954555072058, -96.9676022042631445 34.7673954555072058, -96.9676022042631445 34.9604249436424652, -97.1962841333764089 34.9604249436424652, -97.1962841333764089 34.7673954555072058))",
      List(2, 1, 0)
    ),
    BacksplashImage.fromWkt(
      UUID.randomUUID,
      "s3://radiant-nasa-iserv/2013/09/16/IPR201309161734103489N09712W/IPR201309161734103489N09712W-COG.tif",
      "POLYGON ((-97.2327431049298241 34.7957298689006151, -97.0038341024822302 34.7957298689006151, -97.0038341024822302 34.9887729446974376, -97.2327431049298241 34.9887729446974376, -97.2327431049298241 34.7957298689006151))",
      List(2, 1, 0)
    ),
    BacksplashImage.fromWkt(
      UUID.randomUUID,
      "s3://radiant-nasa-iserv/2013/09/16/IPR201309161734103488N09710W/IPR201309161734103488N09710W-COG.tif",
      "POLYGON ((-97.2146160998055393 34.7815643115317101, -96.9857159234674384 34.7815643115317101, -96.9857159234674384 34.9745999441117306, -97.2146160998055393 34.9745999441117306, -97.2146160998055393 34.7815643115317101))",
      List(2, 1, 0)
    ),
    BacksplashImage.fromWkt(
      UUID.randomUUID,
      "s3://radiant-nasa-iserv/2013/09/16/IPR201309161734103491N09714W/IPR201309161734103491N09714W-COG.tif",
      "POLYGON ((-97.2514251102513043 34.8103246127179347, -97.0225086887154191 34.8103246127179347, -97.0225086887154191 35.0033739451652153, -97.2514251102513043 35.0033739451652153, -97.2514251102513043 34.8103246127179347))",
      List(2, 1, 0)
    ),
    BacksplashImage.fromWkt(
      UUID.randomUUID,
      "s3://radiant-nasa-iserv/2013/09/16/IPR201309161734093494N09717W/IPR201309161734093494N09717W-COG.tif",
      "POLYGON ((-97.2882581209433397 34.8390792006065553, -97.0593257941910963 34.8390792006065553, -97.0593257941910963 35.0321419462086752, -97.2882581209433397 35.0321419462086752, -97.2882581209433397 34.8390792006065553))",
      List(2, 1, 0)
    ),
    BacksplashImage.fromWkt(
      UUID.randomUUID,
      "s3://radiant-nasa-iserv/2013/09/16/IPR201309161734093492N09716W/IPR201309161734093492N09716W-COG.tif",
      "POLYGON ((-97.2695631156167622 34.8244908408028380, -97.0406410351732944 34.8244908408028380, -97.0406410351732944 35.0175449455086323, -97.2695631156167622 35.0175449455086323, -97.2695631156167622 34.8244908408028380))",
      List(2, 1, 0)
    ),
    BacksplashImage.fromWkt(
      UUID.randomUUID,
      "s3://radiant-nasa-iserv/2013/09/16/IPR201309161734093495N09719W/IPR201309161734093495N09719W-COG.tif",
      "POLYGON ((-97.3064081263128458 34.8532402530283179, -97.0774687464257369 34.8532402530283179, -97.0774687464257369 35.0463089466658033, -97.3064081263128458 35.0463089466658033, -97.3064081263128458 34.8532402530283179))",
      List(2, 1, 0)
    ),
    BacksplashImage.fromWkt(
      UUID.randomUUID,
      "s3://radiant-nasa-iserv/2013/09/16/IPR201309161734083496N09721W/IPR201309161734083496N09721W-COG.tif",
      "POLYGON ((-97.3245651316845937 34.8673991150188414, -97.0956172869209126 34.8673991150188414, -97.0956172869209126 35.0604749472389017, -97.3245651316845937 35.0604749472389017, -97.3245651316845937 34.8673991150188414))",
      List(2, 1, 0)
    ),
    BacksplashImage.fromWkt(
      UUID.randomUUID,
      "s3://radiant-nasa-iserv/2013/07/10/IPR201307102027013492N09705W/IPR201307102027013492N09705W-COG.tif",
      "POLYGON ((-97.1618796249273657 34.8246476602902959, -96.9317481640183729 34.8246476602902959, -96.9317481640183729 35.0183310445175806, -97.1618796249273657 35.0183310445175806, -97.1618796249273657 34.8246476602902959))",
      List(2, 1, 0)
    ),
    BacksplashImage.fromWkt(
      UUID.randomUUID,
      "s3://radiant-nasa-iserv/2013/07/10/IPR201307102027003495N09708W/IPR201307102027003495N09708W-COG.tif",
      "POLYGON ((-97.1987316350382713 34.8533963967225588, -96.9685915431843739 34.8533963967225588, -96.9685915431843739 35.0470870449311036, -97.1987316350382713 35.0470870449311036, -97.1987316350382713 34.8533963967225588))",
      List(2, 1, 0)
    ),
    BacksplashImage.fromWkt(
      UUID.randomUUID,
      "s3://radiant-nasa-iserv/2013/07/10/IPR201307102027003496N09710W/IPR201307102027003496N09710W-COG.tif",
      "POLYGON ((-97.2168916400584493 34.8675516523258366, -96.9865339900471071 34.8675516523258366, -96.9865339900471071 35.0612470452516831, -97.2168916400584493 35.0612470452516831, -97.2168916400584493 34.8675516523258366))",
      List(2, 1, 0)
    ),
    BacksplashImage.fromWkt(
      UUID.randomUUID,
      "s3://radiant-nasa-iserv/2013/07/10/IPR201307102027003494N09706W/IPR201307102027003494N09706W-COG.tif",
      "POLYGON ((-97.1800276299428560 34.8388080889003007, -96.9498919253205145 34.8388080889003007, -96.9498919253205145 35.0324950447242713, -97.1800276299428560 35.0324950447242713, -97.1800276299428560 34.8388080889003007))",
      List(2, 1, 0)
    ),
    BacksplashImage.fromWkt(
      UUID.randomUUID,
      "s3://radiant-nasa-iserv/2013/07/10/IPR201307102026593499N09714W/IPR201307102026593499N09714W-COG.tif",
      "POLYGON ((-97.2532286502244290 34.8956473999516206, -97.0228599134702705 34.8956473999516206, -97.0228599134702705 35.0895640458854317, -97.2532286502244290 35.0895640458854317, -97.2532286502244290 34.8956473999516206))",
      List(2, 1, 0)
    ),
    BacksplashImage.fromWkt(
      UUID.randomUUID,
      "s3://radiant-nasa-iserv/2013/07/10/IPR201307102026593501N09716W/IPR201307102026593501N09716W-COG.tif",
      "POLYGON ((-97.2719566554473971 34.9102264693726880, -97.0415808727794342 34.9102264693726880, -97.0415808727794342 35.1041490463194776, -97.2719566554473971 35.1041490463194776, -97.2719566554473971 34.9102264693726880))",
      List(2, 1, 0)
    ),
    BacksplashImage.fromWkt(
      UUID.randomUUID,
      "s3://radiant-nasa-iserv/2014/11/18/IPR201411181603063483N09690W/IPR201411181603063483N09690W-COG.tif",
      "POLYGON ((-97.0162942336370122 34.7347225394073291, -96.7871972821174040 34.7347225394073291, -96.7871972821174040 34.9277129667703861, -97.0162942336370122 34.9277129667703861, -97.0162942336370122 34.7347225394073291))",
      List(2, 1, 0)
    ),
    BacksplashImage.fromWkt(
      UUID.randomUUID,
      "s3://radiant-nasa-iserv/2014/11/18/IPR201411181603053485N09692W/IPR201411181603053485N09692W-COG.tif",
      "POLYGON ((-97.0341650830433764 34.7487163784258328, -96.8052721581621398 34.7487163784258328, -96.8052721581621398 34.9419239672324338, -97.0341650830433764 34.9419239672324338, -97.0341650830433764 34.7487163784258328))",
      List(2, 1, 0)
    ),
    BacksplashImage.fromWkt(
      UUID.randomUUID,
      "s3://radiant-nasa-iserv/2014/11/18/IPR201411181603053487N09696W/IPR201411181603053487N09696W-COG.tif",
      "POLYGON ((-97.0711052494430646 34.7775522648798159, -96.8419868034861935 34.7775522648798159, -96.8419868034861935 34.9707719681521922, -97.0711052494430646 34.9707719681521922, -97.0711052494430646 34.7775522648798159))",
      List(2, 1, 0)
    ),
    BacksplashImage.fromWkt(
      UUID.randomUUID,
      "s3://radiant-nasa-iserv/2014/11/18/IPR201411181603053486N09694W/IPR201411181603053486N09694W-COG.tif",
      "POLYGON ((-97.0528000829760771 34.7633514497023555, -96.8239013186311723 34.7633514497023555, -96.8239013186311723 34.9565639675764643, -97.0528000829760771 34.9565639675764643, -97.0528000829760771 34.7633514497023555))",
      List(2, 1, 0)
    ),
    BacksplashImage.fromWkt(
      UUID.randomUUID,
      "s3://radiant-nasa-iserv/2014/11/18/IPR201411181603043489N09697W/IPR201411181603043489N09697W-COG.tif",
      "POLYGON ((-97.0892052548085616 34.7917502017757698, -96.8598672560023317 34.7917502017757698, -96.8598672560023317 34.9849769687257748, -97.0892052548085616 34.9849769687257748, -97.0892052548085616 34.7917502017757698))",
      List(2, 1, 0)
    ),
    BacksplashImage.fromWkt(
      UUID.randomUUID,
      "s3://radiant-nasa-iserv/2014/11/18/IPR201411181603043492N09701W/IPR201411181603043492N09701W-COG.tif",
      "POLYGON ((-97.1260272637249642 34.8204887728342456, -96.8966887562556138 34.8204887728342456, -96.8966887562556138 35.0137159683539778, -97.1260272637249642 35.0137159683539778, -97.1260272637249642 34.8204887728342456))",
      List(2, 1, 0)
    ),
    BacksplashImage.fromWkt(
      UUID.randomUUID,
      "s3://radiant-nasa-iserv/2014/11/18/IPR201411181603043490N09699W/IPR201411181603043490N09699W-COG.tif",
      "POLYGON ((-97.1078882593042323 34.8063355098849527, -96.8785506264759988 34.8063355098849527, -96.8785506264759988 34.9995619684833272, -97.1078882593042323 34.9995619684833272, -97.1078882593042323 34.8063355098849527))",
      List(2, 1, 0)
    ),
    BacksplashImage.fromWkt(
      UUID.randomUUID,
      "s3://radiant-nasa-iserv/2014/11/18/IPR201411181603033495N09705W/IPR201411181603033495N09705W-COG.tif",
      "POLYGON ((-97.1628732728858466 34.8492214241041296, -96.9335331649403997 34.8492214241041296, -96.9335331649403997 35.0424499680913186, -97.1628732728858466 35.0424499680913186, -97.1628732728858466 34.8492214241041296))",
      List(2, 1, 0)
    ),
    BacksplashImage.fromWkt(
      UUID.randomUUID,
      "s3://radiant-nasa-iserv/2014/11/18/IPR201411181603033493N09703W/IPR201411181603033493N09703W-COG.tif",
      "POLYGON ((-97.1441722681484094 34.8346422648615288, -96.9148343449559349 34.8346422648615288, -96.9148343449559349 35.0278689681038173, -97.1441722681484094 35.0278689681038173, -97.1441722681484094 34.8346422648615288))",
      List(2, 1, 0)
    ),
    BacksplashImage.fromWkt(
      UUID.randomUUID,
      "s3://radiant-nasa-iserv/2014/11/18/IPR201411181603033496N09707W/IPR201411181603033496N09707W-COG.tif",
      "POLYGON ((-97.1810302773136385 34.8633697407041439, -96.9516893584121107 34.8633697407041439, -96.9516893584121107 35.0565989679554164, -97.1810302773136385 35.0565989679554164, -97.1810302773136385 34.8633697407041439))",
      List(2, 1, 0)
    ),
    BacksplashImage.fromWkt(
      UUID.randomUUID,
      "s3://radiant-nasa-iserv/2014/11/18/IPR201411181603023499N09710W/IPR201411181603023499N09710W-COG.tif",
      "POLYGON ((-97.2179112867235347 34.8920925061204912, -96.9883565338897284 34.8920925061204912, -96.9883565338897284 35.0853239677975282, -97.2179112867235347 35.0853239677975282, -97.2179112867235347 34.8920925061204912))",
      List(2, 1, 0)
    ),
    BacksplashImage.fromWkt(
      UUID.randomUUID,
      "s3://radiant-nasa-iserv/2014/11/18/IPR201411181603023500N09712W/IPR201411181603023500N09712W-COG.tif",
      "POLYGON ((-97.2360852913930813 34.9062358815958262, -97.0065286086884555 34.9062358815958262, -97.0065286086884555 35.0994689677731273, -97.2360852913930813 35.0994689677731273, -97.2360852913930813 34.9062358815958262))",
      List(2, 1, 0)
    ),
    BacksplashImage.fromWkt(
      UUID.randomUUID,
      "s3://radiant-nasa-iserv/2014/11/18/IPR201411181603023497N09708W/IPR201411181603023497N09708W-COG.tif",
      "POLYGON ((-97.1991922819786538 34.8775182912259041, -96.9698508297624784 34.8775182912259041, -96.9698508297624784 35.0707479678169207, -97.1991922819786538 35.0707479678169207, -97.1991922819786538 34.8775182912259041))",
      List(2, 1, 0)
    ),
    BacksplashImage.fromWkt(
      UUID.randomUUID,
      "s3://radiant-nasa-iserv/2014/11/07/IPR201411072016213487N09705W/IPR201411072016213487N09705W-COG.tif",
      "POLYGON ((-97.1661392375945070 34.7717649200500674, -96.9389515190430586 34.7717649200500674, -96.9389515190430586 34.9649589372197624, -97.1661392375945070 34.9649589372197624, -97.1661392375945070 34.7717649200500674))",
      List(2, 1, 0)
    ),
    BacksplashImage.fromWkt(
      UUID.randomUUID,
      "s3://radiant-nasa-iserv/2014/11/07/IPR201411072016203491N09711W/IPR201411072016203491N09711W-COG.tif",
      "POLYGON ((-97.2216132531306556 34.8140769432572483, -96.9944043930878479 34.8140769432572483, -96.9944043930878479 35.0072889385538701, -97.2216132531306556 35.0072889385538701, -97.2216132531306556 34.8140769432572483))",
      List(2, 1, 0)
    ),
    BacksplashImage.fromWkt(
      UUID.randomUUID,
      "s3://radiant-nasa-iserv/2014/11/07/IPR201411072016203493N09713W/IPR201411072016203493N09713W-COG.tif",
      "POLYGON ((-97.2402780889471074 34.8282437175410351, -97.0132709237959716 34.8282437175410351, -97.0132709237959716 35.0216749391954139, -97.2402780889471074 35.0216749391954139, -97.2402780889471074 34.8282437175410351))",
      List(2, 1, 0)
    ),
    BacksplashImage.fromWkt(
      UUID.randomUUID,
      "s3://radiant-nasa-iserv/2014/11/07/IPR201411072016193497N09718W/IPR201411072016193497N09718W-COG.tif",
      "POLYGON ((-97.2960472742002196 34.8707286606544002, -97.0688039763841317 34.8707286606544002, -97.0688039763841317 35.0639699408604884, -97.2960472742002196 35.0639699408604884, -97.2960472742002196 34.8707286606544002))",
      List(2, 1, 0)
    ),
    BacksplashImage.fromWkt(
      UUID.randomUUID,
      "s3://radiant-nasa-iserv/2014/11/07/IPR201411072016193494N09715W/IPR201411072016193494N09715W-COG.tif",
      "POLYGON ((-97.2588162636025544 34.8424092224379152, -97.0315900911588187 34.8424092224379152, -97.0315900911588187 35.0356359397111561, -97.2588162636025544 35.0356359397111561, -97.2588162636025544 34.8424092224379152))",
      List(2, 1, 0)
    ),
    BacksplashImage.fromWkt(
      UUID.randomUUID,
      "s3://radiant-nasa-iserv/2014/11/07/IPR201411072016193495N09716W/IPR201411072016193495N09716W-COG.tif",
      "POLYGON ((-97.2771502689214884 34.8563594637219154, -97.0499149718233980 34.8563594637219154, -97.0499149718233980 35.0495939403434491, -97.2771502689214884 35.0495939403434491, -97.2771502689214884 34.8563594637219154))",
      List(2, 1, 0)
    ),
    BacksplashImage.fromWkt(
      UUID.randomUUID,
      "s3://radiant-nasa-iserv/2014/11/07/IPR201411072016183498N09720W/IPR201411072016183498N09720W-COG.tif",
      "POLYGON ((-97.3143942786968807 34.8846636448180334, -97.0869397698153733 34.8846636448180334, -97.0869397698153733 35.0779049406644461, -97.3143942786968807 35.0779049406644461, -97.3143942786968807 34.8846636448180334))",
      List(2, 1, 0)
    )
  )

  val mosaic: Gen[BacksplashMosaic] = images.map({ img =>
    fs2.Stream.eval(IO.pure(img))
  })

  implicit val arbImages = Arbitrary(images)

  implicit val arbMosaic: Arbitrary[BacksplashMosaic] = Arbitrary {
    Gen
      .nonEmptyContainerOf[List, BacksplashMosaic](mosaic)
      .map(_.reduce(_ ++ _))
  }
}
