package io.vertx.aj.demo.models;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.auth.mongo.MongoAuth;
import io.vertx.ext.mongo.FindOptions;
import io.vertx.ext.mongo.MongoClient;

public class PersistenceVerticle extends AbstractVerticle{

  Logger log = LoggerFactory.getLogger(PersistenceVerticle.class);

  // for DB access
  private MongoClient mongoClient;

  // Authentication provider for logging in
  private MongoAuth loginAuthProvider;

  @Override
  public void start(Future<Void> startFuture) {
    // Configure the MongoClient inline.  This should be externalized into a config file
    mongoClient = MongoClient.createShared(vertx, new JsonObject().put("db_name", config().getString("db_name", "aj_demo")).put("connection_string", config().getString("connection_string", "mongodb://localhost:27017")));

    // Configure authentication with MongoDB
    loginAuthProvider = MongoAuth.create(mongoClient, new JsonObject());
    loginAuthProvider.setUsernameField("email");
    loginAuthProvider.setUsernameCredentialField("email");


    EventBus eventBus = vertx.eventBus();
    MessageConsumer<JsonObject> consumer = eventBus.consumer("persistence-address");

    consumer.handler(message -> {

      String action = message.body().getString("action");

      switch (action) {
        case "register-user":
          registerUser(message);
          break;
        case "get-user":
          getUser(message);
          break;
        default:
          message.fail(1, "Unkown action: " + message.body());
      }
    });

    startFuture.complete();

  }

  /**
   * Receive Json in the following format:
   *
    {
     "username": "Jacob",
     "email": "jake@jake.jake",
     "password": "jakejake"
     }
   *
   * and return Json in the following format:
   *
   {
       "email": "jake@jake.jake",
       "token": "jwt.token.here",
       "username": "Jacob",
       "bio": "I work at statefarm",
       "image": null
   }
   *
   * @param message
   */
  private void registerUser(Message<JsonObject> message) {

    JsonObject userToRegister = message.body().getJsonObject("user");

    loginAuthProvider.insertUser(userToRegister.getString("email"), userToRegister.getString("password"), null, null, ar -> {
      if (ar.succeeded()) {
        String id = ar.result();

        JsonObject query = new JsonObject().put("_id", id);
        JsonObject update = new JsonObject()
          .put("$set", new JsonObject().put("username", userToRegister.getString("username")));

        mongoClient.updateCollection("user", query, update, res -> {
          if (res.succeeded()) {
            message.reply(Json.encode(userToRegister));
          }else{
            message.fail(2, "insert failed: " + res.cause().getMessage());
          }
        });

      } else{
        message.fail(2, "insert failed: " + ar.cause().getMessage());
      }
    });

  }


  private void getUser(Message<JsonObject> message) {

//    JsonObject userToReturn = message.body().getJsonObject("user");
    String email = message.body().getString("email");

    JsonObject query = new JsonObject().put("email", email);
    log.info("DB query->"+ query);

    JsonObject obj = new JsonObject().put("email","").put("username","");
    FindOptions options = new FindOptions().setLimit(1).setFields(obj);
    mongoClient.findWithOptions("user", query, options, res -> {
        if (res.succeeded()) {
          JsonObject usr = res.result().get(0);
          usr.remove("_id");
          message.reply(Json.encode(usr));
        }else{
          message.fail(2, "insert failed: " + res.cause().getMessage());
        }
    });
  }


}
