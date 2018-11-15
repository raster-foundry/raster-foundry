package com.rasterfoundry.backsplash.serverless

import io.github.howardjohn.lambda.http4s.Http4sLambdaHandler
import com.rasterfoundry.backsplash.BacksplashServer

class EntryPoint extends Http4sLambdaHandler(BacksplashServer.httpApp)
