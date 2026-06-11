package com.actiongraph.policy;

/**
 * Permission policy that allows every action.
 *
 * <p>This default keeps the runtime usable out of the box. Production
 * applications should supply explicit permission policies for tenant,
 * entitlement, and risk controls.
 */
public final class DefaultPermissionPolicy implements PermissionPolicy {
}
