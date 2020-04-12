package io.vertx.aj.demo;

import io.vertx.aj.demo.models.User;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.JWTAuthHandler;

public class HttpVerticle extends AbstractVerticle{

  Logger log = LoggerFactory.getLogger(HttpVerticle.class);

  JWTAuth jwtAuth;

  @Override
  public void start(Future<Void> startFuture) {

    // Configure authentication with JWT
    jwtAuth = JWTAuth.create(vertx, new JsonObject().put("keyStore", new JsonObject()
      .put("type", "jceks")
      .put("path", "keystore.jceks")
      .put("password", "secret")));

    Router baseRouter = Router.router(vertx);
    Router apiRouter = Router.router(vertx);

    baseRouter.route("/").handler(routingContext -> {
      HttpServerResponse response = routingContext.response();
      response.putHeader("content-type", "text/plain").end("Hello Vert.x Demo!");
    });

    apiRouter.route("/user*").handler(BodyHandler.create());
    apiRouter.post("/users").handler(this::registerUser);
    apiRouter.get("/user/email/:email").handler(JWTAuthHandler.create(jwtAuth)).handler(this::getCurrentUser);

    baseRouter.mountSubRouter("/api", apiRouter);

    vertx.createHttpServer()
      .requestHandler(baseRouter::accept)
      .listen(8080, result -> {
        if (result.succeeded()) {
          startFuture.complete();
        } else {
          startFuture.fail(result.cause());
        }
      });
  }

  private void getCurrentUser(RoutingContext routingContext) {

    String email = routingContext.request().getParam("email");
    log.info("Request param email->"+ email);

    JsonObject message = new JsonObject()
            .put("action", "get-user")
            .put("email", email);

    vertx.eventBus().send("persistence-address", message, ar -> {

      if (ar.succeeded()) {
        User returnedUser = Json.decodeValue(ar.result().body().toString(), User.class);
        String token = jwtAuth.generateToken(new JsonObject().put("email", returnedUser.getEmail()).put("password", returnedUser.getPassword()), new JWTOptions().setIgnoreExpiration(true));
        returnedUser.setToken(token);
        routingContext.response()
          .setStatusCode(201)
          .putHeader("Content-Type", "application/json; charset=utf-8")
          .end(Json.encodePrettily(returnedUser.toConduitJson()));
      }else{
        routingContext.response()
          .setStatusCode(500)
          .putHeader("Content-Type", "application/json; charset=utf-8")
          .end(Json.encodePrettily(ar.cause().getMessage()));

      }
    });
  }

  private void registerUser(RoutingContext routingContext) {

    JsonObject message = new JsonObject()
      .put("action", "register-user")
      .put("user", routingContext.getBodyAsJson().getJsonObject("user"));

    vertx.eventBus().send("persistence-address", message, ar -> {

      if (ar.succeeded()) {
        User returnedUser = Json.decodeValue(ar.result().body().toString(), User.class);
        String token = jwtAuth.generateToken(new JsonObject().put("email", returnedUser.getEmail()).put("password", returnedUser.getPassword()), new JWTOptions().setIgnoreExpiration(true));
        returnedUser.setToken(token);
        routingContext.response()
          .setStatusCode(201)
          .putHeader("Content-Type", "application/json; charset=utf-8")
          //.putHeader("Content-Length", String.valueOf(userResult.toString().length()))
          .end(Json.encodePrettily(returnedUser.toConduitJson()));
      }else{
        routingContext.response()
          .setStatusCode(500)
          .putHeader("Content-Type", "application/json; charset=utf-8")
          //.putHeader("Content-Length", String.valueOf(userResult.toString().length()))
          .end(Json.encodePrettily(ar.cause().getMessage()));

      }
    });

  }
}
