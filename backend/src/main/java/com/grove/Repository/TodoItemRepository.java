package com.grove.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.grove.Domain.TodoItem;

@Repository
public interface TodoItemRepository extends JpaRepository<TodoItem, Long> {
}
