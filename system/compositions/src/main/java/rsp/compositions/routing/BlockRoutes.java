package rsp.compositions.routing;

import rsp.compositions.block.Block;
import rsp.compositions.block.BlockTarget;
import rsp.url.routing.RouteTable;
import rsp.url.routing.RouteTemplate;

/** Type-safe construction DSL for UI block route tables. */
public final class BlockRoutes {
    private BlockRoutes() {
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final RouteTable.Builder<BlockTarget> routes = RouteTable.builder();

        public Builder route(String template, Class<? extends Block<?, ?>> blockClass) {
            return route(template, BlockTarget.of(blockClass));
        }

        public Builder route(String template, Object key, Class<? extends Block<?, ?>> blockClass) {
            return route(template, new BlockTarget(key, blockClass));
        }

        public Builder route(String template, BlockTarget target) {
            routes.route(template, target);
            return this;
        }

        public Builder route(RouteTemplate template, BlockTarget target) {
            routes.route(template, target);
            return this;
        }

        public RouteTable<BlockTarget> build() {
            return routes.build();
        }
    }
}
