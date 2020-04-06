package io.vertx.aj.demo;

import io.vertx.aj.demo.models.PersistenceVerticle;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;

public class MainVerticle extends AbstractVerticle {

  @Override
  public void start(Future<Void> startFuture) {

    CompositeFuture.all(
      deployVerticle(HttpVerticle.class.getName()),
      deployVerticle(PersistenceVerticle.class.getName())
    ).setHandler(f ->{
      if (f.succeeded()) {
        startFuture.complete();
      }else{
        startFuture.fail(f.cause());
      }
    });
  }

  Future<Void> deployVerticle(String verticleName) {
    Future<Void> retVal = Future.future();
    vertx.deployVerticle(verticleName, event -> {
      if (event.succeeded()) {
        retVal.complete();
      }else{
        retVal.fail(event.cause());
      }
    });
    return retVal;
  }

}
