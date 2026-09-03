package com.jucasoliveira.kitchensink.catalog.adapter.persistence.jpa;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * GET_PRODUCT and GET_PRODUCTS — CatalogDAOSQL.xml:78,85.
 *
 * <p>{@code order by name} is not reproduced here. The legacy could sort in the statement because
 * the name lived in the row it was already joining; the name is locale-scoped and the port returns
 * every locale at once, so the sort belongs to CatalogService, where it can use the request's
 * locale.
 */
interface ProductEntityRepository extends JpaRepository<ProductEntity, String> {

    List<ProductEntity> findByCategoryId(String categoryId);

}
