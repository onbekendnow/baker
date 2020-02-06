package com.ing.baker.baas.javadsl

import akka.actor.ActorSystem
import akka.stream.Materializer
import com.ing.baker.baas.scaladsl.{BakerClient => ScalaRemoteBaker}
import com.ing.baker.runtime.javadsl.{Baker => JavaBaker}
import com.ing.baker.runtime.serialization.Encryption

object BakerClient {

  def build(hostname: String, actorSystem: ActorSystem, encryption: Encryption = Encryption.NoEncryption): JavaBaker =
    new JavaBaker(ScalaRemoteBaker.build(hostname)(actorSystem, encryption))
}
