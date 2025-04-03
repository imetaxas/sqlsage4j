package com.spotify.queryfy;

import static com.spotify.hamcrest.pojo.IsPojo.pojo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Test;

final class QueryfyTest {

  @Test
  void test() {
    final QueryfyArticle article = Queryfy.articleBuilder().title("My Article").build();

    assertThat(
        article, is(pojo(QueryfyArticle.class).where(QueryfyArticle::title, is("My Article"))));
  }
}
