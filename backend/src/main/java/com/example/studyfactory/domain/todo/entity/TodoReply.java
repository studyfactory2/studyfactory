package com.example.studyfactory.domain.todo.entity;

import com.example.studyfactory.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "todo_replies")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TodoReply extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long todoItemId;

    @Column(nullable = false)
    private Long memberId;

    @Column(nullable = false)
    private String memberName;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    public TodoReply(Long todoItemId, Long memberId, String memberName, String content) {
        this.todoItemId = todoItemId;
        this.memberId = memberId;
        this.memberName = memberName;
        this.content = content;
    }
}
