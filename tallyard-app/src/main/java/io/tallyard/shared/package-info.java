/**
 * Cross-cutting building blocks shared by every module: identifiers, money arithmetic, tenant
 * context, error model, clock and role conditions. Contains no business rules and depends on no
 * other module.
 */
@org.springframework.modulith.ApplicationModule(type = org.springframework.modulith.ApplicationModule.Type.OPEN)
package io.tallyard.shared;
