package org.mtr.aurelia.pack;

import java.util.List;

/** Everything one {@code aurelia_packs/*.json} file contributed, with the file it came from. */
public record LoadedPack(
		String file,
		String name,
		boolean resourcePack,
		List<PackOperator> operators,
		List<PackLivery> liveries,
		List<PackSound> sounds,
		List<PackStationPreset> stationPresets,
		List<PackTimetable> timetables
) {

	public LoadedPack {
		operators = List.copyOf(operators);
		liveries = List.copyOf(liveries);
		sounds = List.copyOf(sounds);
		stationPresets = List.copyOf(stationPresets);
		timetables = List.copyOf(timetables);
	}

	public int entryCount() {
		return operators.size() + liveries.size() + sounds.size() + stationPresets.size() + timetables.size();
	}
}
