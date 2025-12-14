# Action-Based Auto-Login Workflow

## Overview
The auto-login system uses a flexible action-based workflow where you define custom form actions to handle any website's login structure. Each config consists of:
- **Network Settings**: SSID and Portal URL
- **Form Actions**: Sequence of setValue and click operations

## Architecture

### Models
- **FormAction** (`lib/core/models/form_action.dart`)
  - `FormActionType`: `setValue` or `click`
  - `selector`: CSS selector or element ID
  - `value`: Value to set (for setValue actions)
  - `order`: Execution order

- **WifiConfig** (`lib/core/models/wifi_config.dart`)
  - `ssid`: WiFi network name
  - `url`: Portal login URL
  - `isEnabled`: Whether auto-login is active
  - `actions`: List of FormAction objects

### UI Components
- **ConfigEditorPage** (`lib/features/autologin/presentation/config_editor_page.dart`)
  - Full-page editor
  - Network settings: SSID and URL
  - Action list with expand/collapse tiles
  - Reorderable actions (drag to reorder)
  - Add/remove/edit actions
  - Action configuration: type, selector, value

### Logic
- **AutoLoginHandler** (`lib/features/autologin/logic/autologin_handler.dart`)
  - `_generateJsFromActions()`: Generates JavaScript from action list
  - Actions executed in order based on `order` field
  - Quote escaping for security
  - Event dispatching for React/Angular forms

## Usage

### Creating a Config
1. Open config editor from home screen (+  button)
2. Fill in **Network Settings**:
   - SSID: Exact WiFi network name
   - Portal URL: Login page URL (e.g., http://192.168.1.1)
3. Add **Form Actions**:
   - Tap FAB (+) to add action
   - Configure each action:
     - **Type**: setValue or click
     - **Selector**: CSS selector to target element
     - **Value**: Text to enter (setValue only)
4. Reorder actions by dragging
5. Save

### Action Types

#### setValue
Fills a form field with text
- **Selector**: Targets the input field
- **Value**: Text to enter
- **Example**: 
  ```
  Selector: #username
  Value: myusername
  ```

#### click
Clicks a button or link
- **Selector**: Targets the clickable element
- **No value needed**
- **Example**:
  ```
  Selector: button[type="submit"]
  ```

### Example: Standard Login Form
```
Action 1: setValue
  Selector: #username
  Value: myusername

Action 2: setValue
  Selector: #password
  Value: mypassword

Action 3: click
  Selector: button[type="submit"]
```

### Example: Multi-Step Login
```
Action 1: setValue
  Selector: input.auth-email
  Value: myusername

Action 2: click
  Selector: button#next-step

Action 3: setValue
  Selector: input[data-test="password-field"]
  Value: mypassword

Action 4: click
  Selector: #submit-login
```

## Finding Selectors

Use browser DevTools to find correct selectors:

1. Right-click element → Inspect
2. Check element attributes:
   - `id="username"` → selector: `#username`
   - `name="email"` → selector: `input[name="email"]`
   - `class="login-btn"` → selector: `.login-btn`
3. Test in browser console:
   ```javascript
   document.querySelector('your-selector')
   ```

## Generated JavaScript

The system generates JavaScript based on your actions:

```javascript
try {
  // Action 0: setValue
  var elem_0 = document.querySelector('#username');
  if (elem_0) {
    elem_0.value = 'myusername';
    elem_0.dispatchEvent(new Event('input', { bubbles: true }));
  }

  // Action 1: setValue
  var elem_1 = document.querySelector('#password');
  if (elem_1) {
    elem_1.value = 'mypassword';
    elem_1.dispatchEvent(new Event('input', { bubbles: true }));
  }

  // Action 2: click
  var btn_2 = document.querySelector('button[type="submit"]');
  if (btn_2) {
    btn_2.click();
  }
} catch(e) { console.log('AutoLogin Error:', e); }
```

## Data Persistence & Migration
- Configs saved to flutter_secure_storage as JSON
- Actions serialized as list of maps
- **Backward compatibility**: Old configs with username/password fields are automatically converted to actions on load:
  ```
  username → setValue action with selector "#username, input[name='username']"
  password → setValue action with selector "#password, input[name='password']"
  Submit → click action with selector "button[type='submit'], input[type='submit']"
  ```

## Debugging
1. Check **Debug Screen** (menu → Debug) for logs
2. Look for "Executing JS" to see generated JavaScript
3. Verify selector syntax in browser console first
4. Ensure actions are in correct order
5. Check page content before/after injection in logs

## Tips
- Use specific selectors to avoid ambiguity
- Test selectors in browser DevTools first
- Use `input[name="field"]` if no ID exists
- Click actions don't need values
- setValue actions require a value
- Actions execute sequentially by order
- Add delays between actions if needed (use multiple configs as workaround)
- Quote marks in values are automatically escaped
