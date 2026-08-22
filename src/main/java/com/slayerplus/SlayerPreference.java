package com.slayerplus;

public final class SlayerPreference
{
	private SlayerPreference()
	{
	}

	public enum Workflow
	{
		NORMAL("Normal Slayer"),
		TURAEL_POINT_BOOST("Turael point boosting");

		private final String displayName;

		Workflow(final String displayName)
		{
			this.displayName = displayName;
		}

		@Override
		public String toString()
		{
			return displayName;
		}
	}

	public enum BonusMaster
	{
		MAZCHNA("Mazchna", 2),
		VANNAKA("Vannaka", 3),
		CHAELDAR("Chaeldar", 4),
		DURADEL("Duradel", 5),
		NIEVE_STEVE("Nieve / Steve", 6),
		KONAR("Konar", 8);

		private final String displayName;
		private final int masterId;

		BonusMaster(final String displayName, final int masterId)
		{
			this.displayName = displayName;
			this.masterId = masterId;
		}

		public int getMasterId()
		{
			return masterId;
		}

		@Override
		public String toString()
		{
			return displayName;
		}
	}

	public enum Playstyle
	{
		FAST_XP("Fast XP"),
		PROFIT("Profit");

		private final String displayName;

		Playstyle(final String displayName)
		{
			this.displayName = displayName;
		}

		@Override
		public String toString()
		{
			return displayName;
		}
	}

	public enum CombatStyle
	{
		AUTOMATIC("Automatic"),
		PREFER_MELEE("Prefer Melee"),
		PREFER_RANGED("Prefer Ranged"),
		PREFER_MAGIC("Prefer Magic");

		private final String displayName;

		CombatStyle(final String displayName)
		{
			this.displayName = displayName;
		}

		@Override
		public String toString()
		{
			return displayName;
		}
	}

	public enum Cannon
	{
		PREFER("Prefer"),
		ALLOW("Allow"),
		NEVER("Never");

		private final String displayName;

		Cannon(final String displayName)
		{
			this.displayName = displayName;
		}

		@Override
		public String toString()
		{
			return displayName;
		}
	}

	public enum Burst
	{
		PREFER("Prefer"),
		ALLOW("Allow"),
		NEVER("Never");

		private final String displayName;

		Burst(final String displayName)
		{
			this.displayName = displayName;
		}

		@Override
		public String toString()
		{
			return displayName;
		}
	}

	public enum Travel
	{
		FASTEST("Fastest route"),
		CHEAPEST("Cheapest route"),
		AVOID_CONSUMABLES("Avoid consumable teleports");

		private final String displayName;

		Travel(final String displayName)
		{
			this.displayName = displayName;
		}

		@Override
		public String toString()
		{
			return displayName;
		}
	}

	public enum Shard
	{
		NO_PREFERENCE("No preference"),
		ANCIENT_SHARD("Ancient shard"),
		CRYSTAL_SHARD("Crystal shard"),
		ANCIENT_AND_CRYSTAL_SHARD("Ancient shard + crystal shard");

		private final String displayName;

		Shard(final String displayName)
		{
			this.displayName = displayName;
		}

		@Override
		public String toString()
		{
			return displayName;
		}
	}
}
