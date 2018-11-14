package com.rasterfoundry.backsplash

import io.github.howardjohn.lambda.http4s.Http4sLambdaHandler

class EntryPoint extends Http4sLambdaHandler(BacksplashServer.httpApp)
