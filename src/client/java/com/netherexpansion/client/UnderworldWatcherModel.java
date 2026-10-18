package com.netherexpansion.client;

import com.geckolib.model.DefaultedEntityGeoModel;
import com.geckolib.renderer.base.GeoRenderState;
import net.minecraft.resources.Identifier;

import com.netherexpansion.NetherExpansion;
import com.netherexpansion.entity.UnderworldWatcher;

/**
 * Model: {@code geckolib/models/entity/underworld_watcher.geo.json}. The texture follows the eye state:
 * {@code textures/entity/underworld_watcher<suffix>.png}, each with a matching {@code _glowmask.png}.
 */
public class UnderworldWatcherModel extends DefaultedEntityGeoModel<UnderworldWatcher> {
	public UnderworldWatcherModel() {
		super(NetherExpansion.id("underworld_watcher"));
	}

	@Override
	public Identifier getTextureResource(GeoRenderState renderState) {
		Identifier texture = super.getTextureResource(renderState);
		String eye = renderState.getOrDefaultGeckolibData(UnderworldWatcherRenderer.EYE, "");
		if (eye.isEmpty()) {
			return texture;
		}
		String path = texture.getPath();
		return texture.withPath(path.substring(0, path.length() - ".png".length()) + eye + ".png");
	}
}
