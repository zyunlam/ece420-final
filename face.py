import cv2
import dlib
import os
from pathlib import Path

def batch_process_faces(input_folder, output_folder):
    # 1. Initialize dlib detector
    detector = dlib.get_frontal_face_detector()

    # 2. Setup folders
    if not os.path.exists(output_folder):
        os.makedirs(output_folder)
        print(f"Created output directory: {output_folder}")

    # 3. Define supported image extensions
    valid_extensions = ('.jpg', '.jpeg', '.png', '.bmp', '.webp')

    # 4. Loop through files
    for filename in os.listdir(input_folder):
        if filename.lower().endswith(valid_extensions):
            img_path = os.path.join(input_folder, filename)
            
            # Load image
            img = cv2.imread(img_path)
            if img is None:
                print(f"Skipping {filename}: Could not read image.")
                continue

            # Process
            gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
            faces = detector(gray, 0) # 0 is faster for batch processing

            if len(faces) == 0:
                print(f"No faces found in {filename}")
                continue

            # Crop the first face found
            face = faces[0]
            x, y, w, h = face.left(), face.top(), face.width(), face.height()
            
            # Add a 10% padding
            padding = int(w * 0.1)
            y_start = max(0, y - padding)
            y_end = min(img.shape[0], y + h + padding)
            x_start = max(0, x - padding)
            x_end = min(img.shape[1], x + w + padding)

            face_crop = img[y_start:y_end, x_start:x_end]

            # 5. Save the result
            output_path = os.path.join(output_folder, f"crop_{filename}")
            cv2.imwrite(output_path, face_crop)
            print(f"Successfully processed: {filename}")

    print("\nBatch processing complete!")

# --- EXECUTION ---
# Make sure these folders exist or match your setup

batch_process_faces("app/src/main/assets/faces_old/Tyler_Marrazzo", "app/src/main/assets/faces/Tyler_Marrazzo")