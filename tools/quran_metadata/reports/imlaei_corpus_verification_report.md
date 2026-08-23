# Imlaei Quran Comparison Corpus Verification

Generated: 2026-07-04T11:48:36.453859+00:00

## Source

- Quran.com / Quran Foundation Content API v4 verses/by_chapter?words=true&word_fields=text_imlaei,text_imlaei_simple,text_uthmani,location
- Candidate reference: QUL/TarteelAI Imlaei Simple word-by-word resource, which uses the same location pattern.

## Summary

- Total expected MyVault words: 77430
- Raw remote Imlaei word rows: 77429
- Converted Imlaei rows: 77430
- Matched MyVault word IDs: 77430
- Missing rows after conversion: 0
- Extra rows after conversion: 0
- Duplicate rows after conversion: 0
- Raw segmentation mismatch verses: 7
- Conversion failures: 0

## Segmentation Adjustments

- 2:181: split spaced remote phrase into MyVault word IDs
- 8:6: split spaced remote phrase into MyVault word IDs
- 13:37: split spaced remote phrase into MyVault word IDs
- 15:7: merged remote tokens for 15:7:1
- 27:20: merged remote tokens for 27:20:4
- 36:22: merged remote tokens for 36:22:1
- 37:130: split spaced remote phrase into MyVault word IDs

## First Raw Segmentation Mismatches

- 2:181: local=14, rawRemote=13
- 8:6: local=12, rawRemote=11
- 13:37: local=20, rawRemote=19
- 15:7: local=7, rawRemote=8
- 27:20: local=11, rawRemote=12
- 36:22: local=7, rawRemote=8
- 37:130: local=4, rawRemote=3

## Failures

- None

## First Missing IDs

- None

## First Extra IDs

- None
