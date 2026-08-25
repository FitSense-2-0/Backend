package main.web.services.fitsense.catalog.interfaces.rest.resources;

public record EquipmentTypeResource(Short id, String code, String nameEs, boolean requiresGym, Short displayOrder) {}
