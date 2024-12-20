package service

import model.Category

/**
 * @author guvencenanguvenal
 */
class CategoryService {
    companion object {

        private val categories: MutableSet<Category> = mutableSetOf()

        fun getAllCategories(): MutableSet<Category> {
            return categories
        }
    }
}