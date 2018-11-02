/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package jdocs.akka.persistence.typed;

import akka.Done;
import akka.actor.typed.ActorRef;
import akka.persistence.typed.PersistenceId;
import akka.persistence.typed.javadsl.CommandHandler;
import akka.persistence.typed.javadsl.CommandHandlerBuilder;
import akka.persistence.typed.javadsl.EventHandler;
import akka.persistence.typed.javadsl.PersistentBehavior;

import java.util.Objects;

public class NullBlogState {

  interface BlogEvent {
  }
  public static class PostAdded implements BlogEvent {
    private final String postId;
    private final PostContent content;

    public PostAdded(String postId, PostContent content) {
      this.postId = postId;
      this.content = content;
    }
  }

  public static class BodyChanged implements BlogEvent {
    private final String postId;
    private final String newBody;

    public BodyChanged(String postId, String newBody) {
      this.postId = postId;
      this.newBody = newBody;
    }
  }

  public static class Published implements BlogEvent {
    private final String postId;

    public Published(String postId) {
      this.postId = postId;
    }
  }

  public static class BlogState {
    final PostContent postContent;
    final boolean published;

    BlogState(PostContent postContent, boolean published) {
      this.postContent = postContent;
      this.published = published;
    }

    public BlogState withContent(PostContent newContent) {
      return new BlogState(newContent, this.published);
    }

    public String postId() {
      return postContent.postId;
    }
  }

  public interface BlogCommand {
  }
  public static class AddPost implements BlogCommand {
    final PostContent content;
    final ActorRef<AddPostDone> replyTo;

    public AddPost(PostContent content, ActorRef<AddPostDone> replyTo) {
      this.content = content;
      this.replyTo = replyTo;
    }
  }
  public static class AddPostDone implements BlogCommand {
    final String postId;

    public AddPostDone(String postId) {
      this.postId = postId;
    }
  }
  public static class GetPost implements BlogCommand {
    final ActorRef<PostContent> replyTo;

    public GetPost(ActorRef<PostContent> replyTo) {
      this.replyTo = replyTo;
    }
  }
  public static class ChangeBody implements BlogCommand {
    final String newBody;
    final ActorRef<Done> replyTo;

    public ChangeBody(String newBody, ActorRef<Done> replyTo) {
      this.newBody = newBody;
      this.replyTo = replyTo;
    }
  }
  public static class Publish implements BlogCommand {
    final ActorRef<Done> replyTo;

    public Publish(ActorRef<Done> replyTo) {
      this.replyTo = replyTo;
    }
  }
  public static class PostContent implements BlogCommand {
    final String postId;
    final String title;
    final String body;

    public PostContent(String postId, String title, String body) {
      this.postId = postId;
      this.title = title;
      this.body = body;
    }
  }

  public static class BlogBehavior extends PersistentBehavior<BlogCommand, BlogEvent, BlogState> {

    private CommandHandlerBuilder<BlogCommand, BlogEvent, BlogState, BlogState> initialCommandHandler() {
      return commandHandlerBuilder(Objects::isNull)
          .matchCommand(AddPost.class, cmd -> {
            PostAdded event = new PostAdded(cmd.content.postId, cmd.content);
            return Effect().persist(event)
                .andThen(() -> cmd.replyTo.tell(new AddPostDone(cmd.content.postId)));
          });
    }

    private CommandHandlerBuilder<BlogCommand, BlogEvent, BlogState, BlogState> postCommandHandler() {
      return commandHandlerBuilder(Objects::nonNull)
          .matchCommand(ChangeBody.class, (state, cmd) -> {
            BodyChanged event = new BodyChanged(state.postId(), cmd.newBody);
            return Effect().persist(event).andThen(() -> cmd.replyTo.tell(Done.getInstance()));
          })
          .matchCommand(Publish.class, (state, cmd) -> Effect()
              .persist(new Published(state.postId())).andThen(() -> {
                System.out.println("Blog post published: " + state.postId());
                cmd.replyTo.tell(Done.getInstance());
              }))
          .matchCommand(GetPost.class, (state, cmd) -> {
            cmd.replyTo.tell(state.postContent);
            return Effect().none();
          })
          .matchCommand(AddPost.class, (state, cmd) -> Effect().unhandled());
    }

    public BlogBehavior(PersistenceId persistenceId) {
      super(persistenceId);
    }

    @Override
    public BlogState emptyState() {
      return null;
    }

    @Override
    public CommandHandler<BlogCommand, BlogEvent, BlogState> commandHandler() {
      return initialCommandHandler().orElse(postCommandHandler()).build();
    }

    @Override
    public EventHandler<BlogState, BlogEvent> eventHandler() {
      return eventHandlerBuilder()
        .matchEvent(PostAdded.class, event ->
            new BlogState(event.content, false))
        .matchEvent(BodyChanged.class, (state, chg) ->
            state.withContent(
              new PostContent(state.postId(), state.postContent.title, chg.newBody)))
        .matchEvent(Published.class, (state, event) ->
            new BlogState(state.postContent, true))
        .build();
    }
  }
}
