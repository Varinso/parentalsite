
package com.pa.client.controllers;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class PostRow {
  private final int id;
  private final StringProperty author = new SimpleStringProperty();
  private final StringProperty content = new SimpleStringProperty();
  private final StringProperty imageUrl = new SimpleStringProperty();
  private final StringProperty time = new SimpleStringProperty();

  public PostRow(int id, String author, String content, String time, String imageUrl) {
    this.id = id;
    this.author.set(author);
    this.content.set(content);
    this.time.set(time);
    this.imageUrl.set(imageUrl == null ? "" : imageUrl);
  }

  public int id() {
    return id;
  }

  public StringProperty authorProperty() {
    return author;
  }

  public StringProperty contentProperty() {
    return content;
  }

  public StringProperty timeProperty() {
    return time;
  }

  public StringProperty imageUrlProperty() {
    return imageUrl;
  }
}
