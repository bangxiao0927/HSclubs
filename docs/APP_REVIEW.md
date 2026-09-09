# App Review Account Flows

## Sign In And Legal Consent

Every sign-in starts from the app's sign-in page. The user must select the single checkbox that
accepts both the Terms of Use and Privacy Policy before any Google or password sign-in button is
enabled. A new sign-in attempt requires a new checkbox selection.

The password sign-in is only for App Review credentials supplied in App Store Connect. It is not
an account-registration option and should not be described as a private-email account-creation
alternative for users.

## Guideline 4.8 Response

Use the following response when the reviewer cannot access sign-in or requests Sign in with Apple:

> Hello App Review,
>
> Thank you for reviewing our app.
>
> The screenshot attached to the rejection shows that the "I have read and agree to the Terms of
> Use and Privacy Policy" checkbox was not selected. Until the checkbox is selected, both sign-in
> buttons are intentionally disabled.
>
> To access the password option provided for App Review:
>
> 1. Select the Terms of Use and Privacy Policy checkbox.
> 2. Tap "Sign in with password."
> 3. Enter the review credentials supplied in App Store Connect.
>
> We would also like to clarify why Sign in with Apple is not offered.
>
> HS Clubs is an education app for members of a specific school community. Google sign-in is not
> used as a general-purpose social login. Students and staff use an existing, school-provided
> Google Workspace for Education account, which verifies their institutional identity and
> eligibility for school-member features.
>
> Guideline 4.8 states that another login service is not required when an education app requires
> users to sign in with an existing education account. HS Clubs falls under this exception. An
> Apple account would not establish or verify the user's school affiliation.
>
> The public club catalog can be accessed without signing in. Authentication is required only for
> features associated with an authorized member of the school community.
>
> The app does not use account or interaction data for advertising. Personal information is not
> sold or shared with advertisers.
>
> Please reconsider the Guideline 4.8 rejection under the education-account exception. We would be
> happy to provide further instructions or information.
>
> Thank you.

## Permanent Account Deletion

Use this exact path while signed in:

`Account -> Profile -> Delete account -> type DELETE -> Permanently delete account`

The action permanently removes the user's profile, club memberships and leadership roles,
membership applications, posts, and comments. Club directory entries remain available. Historical
approval references are cleared. The session is invalidated immediately, and the app returns to the
home page with a deletion confirmation.
