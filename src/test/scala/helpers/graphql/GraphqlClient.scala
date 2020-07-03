package helpers.graphql

import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import com.typesafe.config.ConfigFactory
import helpers.users.UserCredentials
import io.circe.generic.auto._
import io.circe.{Decoder, Encoder}
import sangria.ast.Document
import sttp.client.circe.asJson
import sttp.client.{HttpURLConnectionBackend, Identity, NothingT, SttpBackend, basicRequest, _}
import uk.gov.nationalarchives.tdr.GraphQLClient

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.language.postfixOps

class GraphqlClient[Data, Variables](userCredentials: UserCredentials)(implicit val decoder: Decoder[Data], val encoder: Encoder[Variables]) {
  val configuration = ConfigFactory.load

  def userToken: BearerAccessToken = {
    case class AuthResponse(access_token: String)
    implicit val backend: SttpBackend[Identity, Nothing, NothingT] = HttpURLConnectionBackend()

    val body: Map[String, String] = Map(
      "grant_type" -> "password",
      "username" -> userCredentials.userName,
      "password" -> userCredentials.password,
      "client_id" -> "tdr-fe"
    )

    val authUrl = configuration.getString("tdr.auth.url")

    val response = basicRequest
      .body(body)
      .post(uri"$authUrl/auth/realms/tdr/protocol/openid-connect/token")
      .response(asJson[AuthResponse])
      .send()

    val authResponse = response.body match {
      case Right(body) =>
        body
      case Left(e) => throw e
    }
    new BearerAccessToken(authResponse.access_token)
  }

  def result(document: Document, variables: Variables) = {
    val client = new GraphQLClient[Data, Variables](configuration.getString("tdr.api.url"))
    Await.result(client.getResult(userToken, document, Some(variables)), 10 seconds)
  }
}

object GraphqlClient {
  def apply[Data, Variables](userCredentials: UserCredentials)(implicit decoder: Decoder[Data], encoder: Encoder[Variables]): GraphqlClient[Data, Variables] = new GraphqlClient(userCredentials)(decoder, encoder)
}