# Icon assets

The six Android mipmap-nodpi/family_photo_N.jpg resources were produced with the
built-in imagegen tool and technically downscaled to 512×512 JPEG for Android.
Five are processed variants of the five photos supplied for this task; the sixth
is an original heart/paper-plane mark. Source photos are not copied into the repo.

Photo prompt set (one independent call per image, in attachment order):
"identity-preserve; square Android launcher photo icon; preserve both identities,
expressions, geometry, clothing and pose; center both faces in the safe circle;
subtle exposure/clarity; no text, rounded corners, border, logos or added objects."

Author icon prompt:
"white paper airplane whose folded wings form a heart; midnight blue/violet
luminous gradient, restrained rose-gold glow, crisp small-size silhouette;
central 60% safe circle; edge-to-edge square, no text, border or mockup."

Resources are mirrored as family_icon_N.jpg for legacy launchers; Android 8+
uses the matching adaptive-icon XML. The transparent foreground is a native
Android shape resource. All six existing alias component names remain stable.

The five processed photo icons are included in the public repository with the
user's explicit approval for Zaharka0/FamilyGram-Android. The build flag
family_photo_pack_present=true enables all five photo choices and the author mark.
