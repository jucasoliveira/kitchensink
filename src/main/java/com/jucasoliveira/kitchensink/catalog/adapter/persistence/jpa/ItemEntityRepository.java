package com.jucasoliveira.kitchensink.catalog.adapter.persistence.jpa;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * GET_ITEM and GET_ITEMS — CatalogDAOSQL.xml:92,102.
 *
 * <p>SEARCH_ITEMS is not here: its keyword count is only known per request, so
 * {@link JpaCatalogRepository} assembles that statement itself.
 */
interface ItemEntityRepository extends JpaRepository<ItemEntity, String> {

    List<ItemEntity> findByProductId(String productId);

}
