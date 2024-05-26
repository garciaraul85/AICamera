package com.example.myapplication.util

import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue

object QuestionSingleton {
    val questionQueue: BlockingQueue<String> = LinkedBlockingQueue()

    fun addQuestion(question: String) {
        questionQueue.put(question)
    }

    fun clearQuestion() {
        questionQueue.clear()
    }
}