# OmniPose Fit privacy policy — review draft

Prepared October 1, 2026 for package `com.grloepr.pushtrack`. This draft needs an approved developer contact and a public policy URL before store submission.

OmniPose Fit uses the camera, with permission, to estimate body landmarks and joint motion during a training session. App code does not record or upload camera video. Google's bundled ML Kit pose detector processes input images and pose outputs on the device. Motion estimates do not certify exercise technique or provide medical assessment.

Progression choices are stored in Android shared preferences. Android backup and device transfer may retain these preferences according to the user's device backup settings; the current manifest enables Android backup. Uninstalling or clearing app data removes the app's local preferences, while Android backups can have their own retention behavior. Camera access can be revoked through Android settings.

The app has no developer-operated account, ads integration, billing integration or developer-hosted analytics service. Its SDK dependencies add network permissions. Google states that ML Kit can contact its servers for compatibility/model information and sends API performance and utilization metrics to Google. Camera input and pose outputs are not sent to Google by ML Kit. See [Google's ML Kit privacy documentation](https://developers.google.com/ml-kit/terms) and [SDK data-disclosure guidance](https://developers.google.com/ml-kit/android-data-disclosure).

Store Data safety answers must reflect the resolved SDK versions and exact final binary. Do not advertise this build as entirely offline or as collecting no data without reconciling SDK metrics and Android backup behavior.

Developer contact: pending owner approval. No account is created inside the app, and there is no in-app subscription or payment service in this candidate.
