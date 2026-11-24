I need you to redesign the entire UI of my WeChat mini-program. The current design is ugly and low-end. I want a premium, modern, visually stunning UI that can beat 90% of check-in apps on the market.

## Project Context

This is a smart check-in mini-program for:
- Student internship attendance
- Dormitory check-in
- Study tours
- Team building activities

## Current Tech Stack
- WeChat Mini Program (native framework)
- Spring Boot backend
- MySQL database

## Design Requirements

### 1. Overall Style
- **Minimalist, clean, premium** - like Apple/Linear/Notion style
- Lots of white space, subtle shadows, smooth animations
- Premium feel without being flashy
- Use system fonts (San Francisco / PingFang SC)

### 2. Color Palette
- **Primary**: Soft gradient from #667EEA to #764BA2 (purple-blue)
- **Or**: #5B86E5 to #36D1DC (blue-cyan)
- **Or**: #F093FB to #F5576C (pink gradient)
- **Or**: #4FACFE to #00F2FE (blue to cyan)
- Background: #F5F7FA
- Cards: #FFFFFF with subtle shadows
- Text: #1A1A2E (dark), #4A4A6A (secondary), #8888AA (tertiary)

### 3. State Colors (must be distinguishable)
- Normal: #34C759 (green, soft)
- Late: #FF9500 (orange)
- Abnormal: #FF3B30 (red)
- Leave: #8E8E93 (gray)
- Abnormal User: #AF52DE (purple)

### 4. Design Principles
- **No heavy borders** - use shadows and spacing instead
- **Smooth transitions** - all interactions should feel fluid
- **Glassmorphism** - subtle blur effects on overlays
- **Large, readable typography** - 16px minimum for body text
- **Circular avatars** with gradient backgrounds
- **Rounded corners** - 16px-24px for cards, 50% for buttons

### 5. Specific Pages to Redesign

#### Page 1: Login/Profile Setup
- Large centered avatar upload with gradient border
- Clean form with floating labels
- Animated gradient background
- Premium card with glass effect

#### Page 2: Home (Activity List)
- Segmented control with slide animation: "Created | Managing | Joined"
- Activity cards with left color accent bar
- Each card shows: activity name, time, date, status badge
- Floating action button with gradient (Create Activity)
- Pull-to-refresh with animation
- Empty state with illustration

#### Page 3: Activity Detail (Participant)
- Hero header with activity name and status
- Quick action buttons: Check-in, Leave, Exit (3D touch style)
- Today's status card with animated indicator
- Monthly calendar with dot indicators
- List of check-in history with timestamps

#### Page 4: Activity Admin (Manager View)
- Stats dashboard with 5 metric cards in grid
- Animated number counters
- Filterable member list with search
- Each member row: avatar, name, status badge, stats
- Action buttons: Export, Roster Import, Manage Members

#### Page 5: Check-in Page
- **Location mode**: Full-screen map with animated pulse pin, distance indicator, large confirm button
- **Face mode**: Clean camera overlay with watermark, circle guide, retake/confirm buttons

#### Page 6: Leave Application
- Clean form with icons
- Select approver with avatar list
- Submit button with gradient

#### Page 7: Profile Page
- Large header with avatar and name
- Stats summary (total check-ins, streak, etc.)
- Menu items with icons and arrow
- Logout button with red text

### 6. Micro-interactions Required
- Spring animation on button press
- Smooth tab switching
- Skeleton loading states
- Success/error toast with icons
- Haptic feedback on important actions

### 7. Custom Components Needed
- GradientButton (with loading state)
- GlassCard (with blur)
- StatusBadge (with dot)
- AnimatedCounter
- SlideSegmentedControl
- CustomToast
- SkeletonLoader

### 8. Output Format
For each page, generate:
1. Full WXML with semantic structure
2. Complete WXSS with animations and responsive design
3. Clean JS with smooth interactions
4. Show me what makes it premium - explain the design choices

## Important Notes

**DO NOT use:**
- Boring flat design
- Heavy borders or lines
- Taboo colors (neon, too bright, mismatched)
- Overcrowded layouts
- Default WeChat styles

**DO use:**
- Gradient backgrounds on key elements
- Subtle shadows (box-shadow with low opacity)
- Smooth animations (300ms transitions)
- Generous spacing (at least 16px padding)
- Icon + text combinations
- Dark mode compatibility consideration

## Start with Page 1 (Login/Profile Setup) first

Please generate the complete code and explain why this design looks premium. I want the UI to be so good that users would want to show it to their friends.