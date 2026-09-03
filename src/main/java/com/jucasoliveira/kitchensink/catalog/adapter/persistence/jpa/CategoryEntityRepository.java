package com.jucasoliveira.kitchensink.catalog.adapter.persistence.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

/** GET_CATEGORY and GET_CATEGORIES — CatalogDAOSQL.xml:64,71. */
interface CategoryEntityRepository extends JpaRepository<CategoryEntity, String> {
}
